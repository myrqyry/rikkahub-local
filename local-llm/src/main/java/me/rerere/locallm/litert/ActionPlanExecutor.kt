package me.rerere.locallm.litert

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
 */
class ActionPlanExecutor(
    private val compiler: ActionPlanCompiler = DefaultActionPlanCompiler(),
    private val directExecutor: DirectToolExecutor = DirectToolExecutor(),
    private val zeroWorkflowExecutor: ZeroWorkflowExecutor? = null,
) {
    suspend fun execute(plan: ActionPlan): ActionPlanResult {
        val context = CompilationContext(
            toolCatalog = LiteRtToolBridgeRegistry.snapshot().associateBy { it.name },
        )
        return when (val compiled = compiler.compile(plan, context)) {
            is CompilationResult.Valid -> dispatch(compiled.plan)
            is CompilationResult.Repaired -> dispatch(compiled.repairedPlan)
            is CompilationResult.Invalid -> ActionPlanResult.Failed(
                compiled.diagnostics.joinToString("; ") { "${it.code}(${it.step}): ${it.message}" },
            )
        }
    }

    private suspend fun dispatch(plan: ActionPlan): ActionPlanResult {
        return when (plan) {
            is ActionPlan.ToolCall -> directExecutor.execute(plan)
            is ActionPlan.WorkflowCall -> zeroWorkflowExecutor?.execute(plan)
                ?: ActionPlanResult.Failed("workflow_execution_not_implemented")
        }
    }
}
