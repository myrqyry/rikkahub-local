package me.rerere.locallm.litert

/**
 * Phase 2 — the compound-procedure execution path. [ActionPlan.WorkflowCall] plans are
 * handed to an implementation of this seam instead of being executed inline.
 *
 * The interface lives in `local-llm` (which must not depend on `app`) while the real
 * implementation adapts the app-side [WorkflowEngine]; the provider receives it via DI and
 * threads it into [LiteRtToolBridge]. When no implementation is wired, the bridge returns
 * `workflow_execution_not_implemented` rather than silently dropping the plan.
 */
fun interface ZeroWorkflowExecutor {
    suspend fun execute(plan: ActionPlan.WorkflowCall): ActionPlanResult
}
