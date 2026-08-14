package me.rerere.rikkahub.data.agentrun

import me.rerere.locallm.litert.ActionPlan
import me.rerere.locallm.litert.ActionPlanExecutor
import me.rerere.locallm.litert.Postcondition
import me.rerere.locallm.litert.ShadowPlanner
import me.rerere.locallm.litert.mesh.ExecutionResult
import me.rerere.locallm.litert.mesh.ExecutorAgent
import me.rerere.locallm.litert.mesh.MicroAgentContext

/**
 * ExecutorAgent adapter (roadmap D4).
 *
 * Routes an already-accepted plan EXCLUSIVELY through the deterministic execution stack
 * (ShadowPlanner -> compiler -> capability broker -> ActionPlanExecutor). The adapter holds
 * no direct [me.rerere.ai.core.Tool] references, so it cannot bypass the compile /
 * authorize / execute pipeline. No-role-bypass: this role has the same execution authority
 * as the stack, and nothing more.
 */
class DefaultExecutorAgent(
    private val actionPlanExecutor: ActionPlanExecutor,
) : ExecutorAgent {

    override suspend fun execute(
        plan: ActionPlan,
        postconditions: List<Postcondition>,
        context: MicroAgentContext,
        shadowPlanner: ShadowPlanner?,
    ): ExecutionResult {
        val outcome = actionPlanExecutor.execute(plan)
        return ExecutionResult(result = outcome, receiptRef = null)
    }
}
