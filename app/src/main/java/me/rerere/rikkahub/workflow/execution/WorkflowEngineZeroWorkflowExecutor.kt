package me.rerere.rikkahub.workflow.execution

import me.rerere.ai.ui.UIMessagePart
import me.rerere.locallm.litert.ActionPlan
import me.rerere.locallm.litert.ActionPlanResult
import me.rerere.locallm.litert.ZeroWorkflowExecutor
import me.rerere.rikkahub.workflow.model.WorkflowRunStatus

/**
 * Phase 2 — app-side adapter that turns an [ActionPlan.WorkflowCall] into a real workflow
 * fire. Lives in `app` (not `local-llm`) because [WorkflowEngine] lives here; the seam
 * itself is defined in `local-llm` as [ZeroWorkflowExecutor].
 *
 * The workflow executes against its own persisted definition (triggers, conditions, action
 * sequence, HARDLINE checks, cooldown/daily-cap gates all live inside [WorkflowEngine.fire]).
 * A non-SUCCESS outcome — including every SKIPPED_* gate — is reported as a failure so the
 * caller sees why the plan didn't complete.
 */
class WorkflowEngineZeroWorkflowExecutor(
    private val engine: WorkflowEngine,
) : ZeroWorkflowExecutor {

    override suspend fun execute(plan: ActionPlan.WorkflowCall): ActionPlanResult {
        val outcome = engine.fire(plan.workflowId)
        return if (outcome.status == WorkflowRunStatus.SUCCESS) {
            val summary = outcome.summary.ifBlank { "workflow '${plan.workflowId}' completed" }
            ActionPlanResult.Success(listOf(UIMessagePart.Text(summary)))
        } else {
            ActionPlanResult.Failed(
                "workflow_${outcome.status.name.lowercase()}: ${outcome.error ?: "workflow did not complete"}",
            )
        }
    }
}
