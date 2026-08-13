package me.rerere.locallm.litert

import android.util.Log

/**
 * Phase 2 — the direct execution path. Runs a single Rikka tool call from an
 * [ActionPlan.ToolCall], enforcing the plan's [CapabilityGrant] before invoking the
 * tool. Used for simple, single-shot tools; compound procedures route through
 * [ZeroWorkflowExecutor].
 */
class DirectToolExecutor {

    suspend fun execute(plan: ActionPlan.ToolCall): ActionPlanResult {
        val capability = plan.toolName
        if (!plan.grant.isAllowed(capability)) {
            Log.w(TAG, "capability_rejected: $capability (granted: ${plan.grant.grantedCapabilities})")
            return ActionPlanResult.CapabilityRejected(
                capability = capability,
                reason = "Tool '$capability' is not in the granted capability set for this request.",
            )
        }

        val tool = LiteRtToolBridgeRegistry.lookup(plan.toolName)
            ?: return ActionPlanResult.Failed("tool_not_found: ${plan.toolName}")

        return try {
            ActionPlanResult.Success(tool.execute(plan.args))
        } catch (t: Throwable) {
            Log.w(TAG, "tool_execute_threw: ${plan.toolName}", t)
            ActionPlanResult.Failed("${t::class.simpleName}: ${t.message.orEmpty()}".take(500))
        }
    }

    companion object {
        private const val TAG = "DirectToolExecutor"
    }
}
