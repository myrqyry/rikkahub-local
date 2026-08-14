package me.rerere.locallm.litert.mesh

import kotlinx.serialization.Serializable
import me.rerere.locallm.litert.ActionPlan
import me.rerere.locallm.litert.ActionPlanResult
import me.rerere.locallm.litert.Postcondition
import me.rerere.locallm.litert.PostconditionResult

/**
 * Typed protocol bodies (roadmap D3). The EventMesh is transport; these are the protocol.
 * Roles interpret typed message bodies, never arbitrary JSON payloads. All types are
 * serializable so the protocol survives the wire / persistence layer.
 */
@Serializable
sealed interface MicroAgentMessage

/* ---- Planning ---- */

@Serializable
data class PlanRequested(
    val userRequest: String,
) : MicroAgentMessage

@Serializable
data class PlanProposed(
    val plan: ActionPlan,
    val requestedPostconditions: List<Postcondition> = emptyList(),
) : MicroAgentMessage

/* ---- Review ---- */

@Serializable
data class ReviewRequested(
    val plan: ActionPlan,
    val requestedPostconditions: List<Postcondition> = emptyList(),
) : MicroAgentMessage

@Serializable
sealed interface ReviewDecision : MicroAgentMessage {
    @Serializable
    data class Accepted(
        val plan: ActionPlan,
        val postconditions: List<Postcondition>,
    ) : ReviewDecision

    @Serializable
    data class Rejected(
        val code: String,
        val reason: String,
    ) : ReviewDecision
}

/* ---- Execution ---- */

@Serializable
data class ExecutionRequested(
    val plan: ActionPlan,
    val postconditions: List<Postcondition> = emptyList(),
) : MicroAgentMessage

@Serializable
data class ExecutionResult(
    val result: ActionPlanResult,
    val receiptRef: String? = null,
) : MicroAgentMessage

/* ---- Verification ---- */

@Serializable
data class VerificationRequested(
    val result: ActionPlanResult,
    val postconditions: List<Postcondition> = emptyList(),
) : MicroAgentMessage

@Serializable
data class VerificationResult(
    val result: PostconditionResult,
) : MicroAgentMessage

/* ---- Orchestration ---- */

@Serializable
data class OrchestrationResult(
    val status: String,
    val summary: String,
    val result: ActionPlanResult? = null,
    val postcondition: PostconditionResult? = null,
) : MicroAgentMessage
