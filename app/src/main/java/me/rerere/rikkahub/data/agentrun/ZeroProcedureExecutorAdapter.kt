package me.rerere.rikkahub.data.agentrun

import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.locallm.litert.ActionPlan
import me.rerere.locallm.litert.ActionPlanResult
import me.rerere.locallm.litert.PostconditionResult
import me.rerere.locallm.litert.PostconditionVerifier
import me.rerere.locallm.litert.ZeroProcedureExecutor
import me.rerere.locallm.litert.zero.ZeroProcedureEngine
import me.rerere.locallm.litert.zero.ZeroCompilationResult
import me.rerere.locallm.litert.zero.ZeroProcedureReceiptSink

/**
 * App-side [ZeroProcedureExecutor] (roadmap B5). Routes an `ActionPlan.ProcedureCall` through
 * the deterministic [ZeroProcedureEngine]:
 *
 *   repository lookup by id → compile against the tool catalog (Invalid → Failed with
 *   diagnostics) → capability-verify each step against [ActionPlan.ProcedureCall.grant] →
 *   execute → map [ZeroProcedureEngine.execute] outcome to an [ActionPlanResult].
 *
 * The tool catalog is built from `LiteRtToolBridgeRegistry.snapshot()`, the same source
 * `ActionPlanExecutor` uses for its compile context. ZeroProcedureEngine does no capability
 * gating of its own; that gate lives here against the plan's grant.
 */
class ZeroProcedureExecutorAdapter(
    private val repository: ZeroProcedureRepository,
    private val engine: ZeroProcedureEngine,
    private val toolCatalog: Map<String, Tool>,
    private val receiptSink: ZeroProcedureReceiptSink? = null,
    private val verifier: PostconditionVerifier = PostconditionVerifier(),
) : ZeroProcedureExecutor {

    override suspend fun execute(plan: ActionPlan.ProcedureCall): ActionPlanResult {
        val procedure = repository.getEnabled(plan.procedureId)
            ?: return ActionPlanResult.Failed("procedure_not_found: ${plan.procedureId}")

        when (val compiled = engine.compile(procedure, toolCatalog)) {
            is ZeroCompilationResult.Invalid -> {
                val diagnostics = compiled.diagnostics.joinToString("; ") { "${it.code}(${it.stepId}): ${it.message}" }
                return ActionPlanResult.Failed("procedure_compile_invalid: $diagnostics")
            }
            is ZeroCompilationResult.Valid -> Unit
        }

        // Capability gate: every step's tool must be within the granted capability set.
        for (step in procedure.steps) {
            if (!plan.grant.isAllowed(step.tool)) {
                return ActionPlanResult.CapabilityRejected(
                    capability = step.tool,
                    reason = "Procedure '${plan.procedureId}' step '${step.stepId}' requires tool '${step.tool}', which is not in the granted capability set.",
                )
            }
        }

        val (result, receipt) = engine.executeWithReceipts(
            procedure,
            toolCatalog,
            procedureRevision = repository.revisionOf(plan.procedureId),
        )

        // C4: a successful execution is distinct from a verified success. Execution may
        // succeed while a postcondition fails; do NOT fall back to another candidate here.
        var postcondition: PostconditionResult = PostconditionResult.Passed
        if (result.success && procedure.postconditions.isNotEmpty()) {
            postcondition = verifier.verify(procedure.postconditions, result.outputs)
        }
        runCatching { receiptSink?.record(receipt, postcondition) }

        if (!result.success) {
            val detail = result.error ?: result.stepResults
                .filterNot { it.success }
                .joinToString("; ") { "${it.stepId}: ${it.error}" }
            return ActionPlanResult.Failed("procedure_failed: $detail")
        }
        if (postcondition is PostconditionResult.Failed) {
            return ActionPlanResult.Failed(
                "postcondition_failed: ${postcondition.code}: ${postcondition.detail}",
            )
        }

        val summary = procedure.description
            ?: "procedure '${plan.procedureId}' completed (${result.stepResults.size} steps)"
        return ActionPlanResult.Success(listOf(UIMessagePart.Text(summary)))
    }
}
