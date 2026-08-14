package me.rerere.rikkahub.data.agentrun

import me.rerere.locallm.litert.ActionPlan
import me.rerere.locallm.litert.ActionPlanCompiler
import me.rerere.locallm.litert.CompilationContext
import me.rerere.locallm.litert.CompilationResult
import me.rerere.locallm.litert.DefaultActionPlanCompiler
import me.rerere.locallm.litert.LiteRtToolBridgeRegistry
import me.rerere.locallm.litert.Postcondition
import me.rerere.locallm.litert.mesh.MicroAgentContext
import me.rerere.locallm.litert.mesh.ReviewDecision
import me.rerere.locallm.litert.mesh.ReviewerAgent

/**
 * ReviewerAgent adapter (roadmap D4).
 *
 * Deterministic reviewer: checks plan shape by compiling the plan against the current tool
 * catalog ([LiteRtToolBridgeRegistry]). A [CompilationResult.Valid] or
 * [CompilationResult.Repaired] plan is accepted as-is (the executor re-compiles and
 * dispatches the repaired plan); an [CompilationResult.Invalid] plan is rejected with
 * code `PLAN_INVALID` and the joined diagnostics. No LLM call.
 */
class DefaultReviewerAgent(
    private val compiler: ActionPlanCompiler = DefaultActionPlanCompiler(),
) : ReviewerAgent {

    override suspend fun review(
        plan: ActionPlan,
        requestedPostconditions: List<Postcondition>,
        context: MicroAgentContext,
    ): ReviewDecision {
        val compileContext = CompilationContext(
            toolCatalog = LiteRtToolBridgeRegistry.snapshot().associateBy { it.name },
        )
        return when (val compiled = compiler.compile(plan, compileContext)) {
            is CompilationResult.Valid -> ReviewDecision.Accepted(plan, requestedPostconditions)
            is CompilationResult.Repaired -> ReviewDecision.Accepted(plan, requestedPostconditions)
            is CompilationResult.Invalid -> ReviewDecision.Rejected(
                code = "PLAN_INVALID",
                reason = compiled.diagnostics.joinToString("; ") { "${it.code}(${it.step}): ${it.message}" },
            )
        }
    }
}
