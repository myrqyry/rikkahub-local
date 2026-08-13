package me.rerere.locallm.litert

/**
 * Phase 2 — routes an [ActionPlan] to the correct execution path:
 *  - [ActionPlan.ToolCall]    → [DirectToolExecutor] (simple, single-shot tools)
 *  - [ActionPlan.WorkflowCall] → [ZeroWorkflowExecutor] (compound procedures)
 *
 * The [zeroWorkflowExecutor] seam is nullable because `local-llm` cannot depend on the
 * app-side [WorkflowEngine] implementation; when it is absent, a WorkflowCall plan fails
 * loudly instead of being silently dropped.
 */
class ActionPlanExecutor(
    private val directExecutor: DirectToolExecutor = DirectToolExecutor(),
    private val zeroWorkflowExecutor: ZeroWorkflowExecutor? = null,
) {
    suspend fun execute(plan: ActionPlan): ActionPlanResult {
        return when (plan) {
            is ActionPlan.ToolCall -> directExecutor.execute(plan)
            is ActionPlan.WorkflowCall -> zeroWorkflowExecutor?.execute(plan)
                ?: ActionPlanResult.Failed("workflow_execution_not_implemented")
        }
    }
}
