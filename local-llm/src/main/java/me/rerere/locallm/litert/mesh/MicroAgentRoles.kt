package me.rerere.locallm.litert.mesh

import me.rerere.locallm.litert.ActionPlan
import me.rerere.locallm.litert.ActionPlanResult
import me.rerere.locallm.litert.Postcondition
import me.rerere.locallm.litert.PostconditionResult
import me.rerere.locallm.litert.ShadowPlanner

/**
 * The four production role adapters (roadmap D4). Each is deliberately narrow. No role
 * receives direct [me.rerere.ai.core.Tool] references — deterministic layers retain
 * execution authority; the mesh only coordinates decisions.
 *
 * These are the *agent* seams. Concrete adapters are wired via DI in the app module
 * (mirroring ZeroProcedureExecutorAdapter / CandidateEvaluationTraceSink) and route through
 * the existing ShadowPlanner → compiler → capability broker → ActionPlanExecutor stack.
 */

/**
 * Planner: user request → candidate [ActionPlan] + postcondition proposal.
 * May use a model. Authorizes nothing.
 */
fun interface PlannerAgent {
    suspend fun propose(request: String, context: MicroAgentContext): PlanProposed
}

/**
 * Reviewer: plan shape, compiler status, capabilities, effects, resource budget, obvious
 * contradiction, postcondition presence. Mostly deterministic — do not add an LLM call
 * merely because the class is named Reviewer.
 */
fun interface ReviewerAgent {
    suspend fun review(
        plan: ActionPlan,
        requestedPostconditions: List<Postcondition>,
        context: MicroAgentContext,
    ): ReviewDecision
}

/**
 * Executor: receives an already-accepted plan and routes it through the established
 * deterministic execution stack (ShadowPlanner → compiler → capability broker →
 * ActionPlanExecutor). Receives no direct [Tool] references. Non-negotiable.
 */
fun interface ExecutorAgent {
    suspend fun execute(
        plan: ActionPlan,
        postconditions: List<Postcondition>,
        context: MicroAgentContext,
        shadowPlanner: ShadowPlanner?,
    ): ExecutionResult
}

/**
 * Verifier: uses the C4 [me.rerere.locallm.litert.PostconditionVerifier]. Decides
 * VERIFIED / FAILED. Never reruns a mutating operation.
 */
fun interface VerifierAgent {
    suspend fun verify(
        result: ActionPlanResult,
        postconditions: List<Postcondition>,
        context: MicroAgentContext,
    ): PostconditionResult
}
