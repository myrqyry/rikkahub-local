package me.rerere.locallm.litert

import java.util.UUID

/**
 * Phase 2/3 — routes an [ActionPlan] through the deterministic compile gate, then to the
 * correct execution path:
 *  - [ActionPlan.ToolCall]    → [DirectToolExecutor] (simple, single-shot tools)
 *  - [ActionPlan.WorkflowCall] → [ZeroWorkflowExecutor] (compound procedures)
 *
 * Every plan is compiled by [compiler] before execution:
 *  - [CompilationResult.Valid]      → dispatched as-is
 *  - [CompilationResult.Repaired]   → the repaired plan is dispatched (lossless repairs only)
 *  - [CompilationResult.Invalid]    → fails loudly with the diagnostics; nothing executes
 *
 * The [zeroWorkflowExecutor] seam is nullable because `local-llm` cannot depend on the
 * app-side [WorkflowEngine] implementation; when it is absent, a WorkflowCall plan fails
 * loudly instead of being silently dropped.
 *
 * Phase 4 — every execution (including compile-invalid and capability-rejected ones) emits
 * a [WorkflowReceipt] through [receiptSink] for the app-side audit trail. A null sink is
 * safe: only observability is lost, never execution.
 */
class ActionPlanExecutor(
    private val compiler: ActionPlanCompiler = DefaultActionPlanCompiler(),
    private val directExecutor: DirectToolExecutor = DirectToolExecutor(),
    private val zeroWorkflowExecutor: ZeroWorkflowExecutor? = null,
    private val receiptSink: WorkflowReceiptSink? = null,
) {
    suspend fun execute(plan: ActionPlan): ActionPlanResult {
        val requestedAtMs = System.currentTimeMillis()
        val context = CompilationContext(
            toolCatalog = LiteRtToolBridgeRegistry.snapshot().associateBy { it.name },
        )
        val compiled = compiler.compile(plan, context)
        val result = when (compiled) {
            is CompilationResult.Valid -> dispatch(compiled.plan)
            is CompilationResult.Repaired -> dispatch(compiled.repairedPlan)
            is CompilationResult.Invalid -> ActionPlanResult.Failed(
                compiled.diagnostics.joinToString("; ") { "${it.code}(${it.step}): ${it.message}" },
            )
        }
        receiptSink?.record(buildReceipt(plan, compiled, result, requestedAtMs))
        return result
    }

    private suspend fun dispatch(plan: ActionPlan): ActionPlanResult {
        return when (plan) {
            is ActionPlan.ToolCall -> directExecutor.execute(plan)
            is ActionPlan.WorkflowCall -> zeroWorkflowExecutor?.execute(plan)
                ?: ActionPlanResult.Failed("workflow_execution_not_implemented")
        }
    }

    private fun buildReceipt(
        plan: ActionPlan,
        compiled: CompilationResult,
        result: ActionPlanResult,
        requestedAtMs: Long,
    ): WorkflowReceipt {
        val (repairs, diagnostics, compileOutcome) = when (compiled) {
            is CompilationResult.Valid -> Triple(emptyList<String>(), emptyList<String>(), "valid")
            is CompilationResult.Repaired -> Triple(
                compiled.repairs.map { "${it.code}(${it.step}): ${it.message}" },
                emptyList(),
                "repaired",
            )
            is CompilationResult.Invalid -> Triple(
                emptyList(),
                compiled.diagnostics.map { "${it.code}(${it.step}): ${it.message}" },
                "invalid",
            )
        }
        val (status, errorMessage) = when (result) {
            is ActionPlanResult.Success -> "succeeded" to null
            is ActionPlanResult.Failed -> "failed" to result.errorMessage
            is ActionPlanResult.CapabilityRejected -> "capability_rejected" to result.reason
        }
        return WorkflowReceipt(
            receiptId = UUID.randomUUID().toString(),
            kind = if (plan is ActionPlan.ToolCall) "tool" else "workflow",
            domainId = when (plan) {
                is ActionPlan.ToolCall -> plan.toolName
                is ActionPlan.WorkflowCall -> plan.workflowId
            },
            requestedAtMs = requestedAtMs,
            compileOutcome = compileOutcome,
            repairs = repairs,
            diagnostics = diagnostics,
            grantedCapabilities = plan.grant.grantedCapabilities,
            status = status,
            errorMessage = errorMessage,
            durationMs = System.currentTimeMillis() - requestedAtMs,
        )
    }
}
