package me.rerere.locallm.decision

/**
 * A narrow decision request suitable for a System-One-style model.
 *
 * Decision engines answer typed boolean / choice / score questions. They do not
 * generate prose, execute tools, or own capability authority.
 */
data class DecisionRequest(
    val state: String,
    val questions: List<DecisionQuestion>,
)

/** Typed question shapes supported by a decision-only backend. */
sealed interface DecisionQuestion {
    val id: String
    val instructions: String

    data class Boolean(
        override val id: String,
        override val instructions: String,
    ) : DecisionQuestion

    data class Choice(
        override val id: String,
        override val instructions: String,
        val options: List<String>,
    ) : DecisionQuestion {
        init {
            require(options.size >= 2) { "choice questions require at least two options" }
            require(options.distinct().size == options.size) { "choice options must be unique" }
        }
    }

    data class Score(
        override val id: String,
        override val instructions: String,
        val min: Int,
        val max: Int,
    ) : DecisionQuestion {
        init {
            require(min < max) { "score range must contain at least two values" }
        }
    }
}

/** Typed answers returned by a decision-only backend. */
sealed interface DecisionAnswer {
    data class Boolean(
        val probability: Double,
    ) : DecisionAnswer {
        init {
            require(probability in 0.0..1.0) { "boolean probability must be in [0, 1]" }
        }
    }

    data class Choice(
        val value: String,
        val probabilities: Map<String, Double> = emptyMap(),
    ) : DecisionAnswer {
        init {
            require(probabilities.values.all { it in 0.0..1.0 }) {
                "choice probabilities must be in [0, 1]"
            }
        }
    }

    data class Score(
        val value: Int,
        val probabilities: Map<Int, Double> = emptyMap(),
    ) : DecisionAnswer {
        init {
            require(probabilities.values.all { it in 0.0..1.0 }) {
                "score probabilities must be in [0, 1]"
            }
        }
    }
}

data class DecisionResult(
    val answers: Map<String, DecisionAnswer>,
    val provider: String,
    val latencyMs: Long,
)

/**
 * Runtime seam for a local or explicitly-approved remote decision backend.
 *
 * Implementations may wrap a tiny local classifier/decision model or a hosted
 * System-One provider. They must not perform side effects.
 */
fun interface SystemOneDecisionEngine {
    suspend fun evaluate(request: DecisionRequest): DecisionResult
}

/** Shape of work being routed through the reasoning ladder. */
enum class ReasoningWorkKind {
    BOOLEAN_DECISION,
    CHOICE_DECISION,
    SCORE_DECISION,
    SYNTHESIS,
}

enum class ReasoningTier {
    DETERMINISTIC,
    SYSTEM_ONE_LOCAL,
    SYSTEM_ONE_REMOTE,
    GENERATIVE,
}

data class ReasoningAvailability(
    val localSystemOne: Boolean = false,
    val remoteSystemOne: Boolean = false,
    val remoteSystemOneAllowed: Boolean = false,
)

data class ReasoningSelection(
    val tier: ReasoningTier,
    val reason: String,
)

/**
 * Pure routing policy for the Meristem/RikkaHub reasoning ladder:
 * deterministic -> local System One -> approved remote System One -> generative.
 *
 * This selects a reasoning mechanism only. It grants no capabilities and cannot
 * bypass the capability/effect broker.
 */
object ReasoningRoutingPolicy {
    fun select(
        deterministicResolved: Boolean,
        workKind: ReasoningWorkKind,
        availability: ReasoningAvailability,
    ): ReasoningSelection {
        if (deterministicResolved) {
            return ReasoningSelection(
                tier = ReasoningTier.DETERMINISTIC,
                reason = "deterministic_path_resolved",
            )
        }

        if (workKind == ReasoningWorkKind.SYNTHESIS) {
            return ReasoningSelection(
                tier = ReasoningTier.GENERATIVE,
                reason = "synthesis_required",
            )
        }

        if (availability.localSystemOne) {
            return ReasoningSelection(
                tier = ReasoningTier.SYSTEM_ONE_LOCAL,
                reason = "local_decision_backend_available",
            )
        }

        if (availability.remoteSystemOne && availability.remoteSystemOneAllowed) {
            return ReasoningSelection(
                tier = ReasoningTier.SYSTEM_ONE_REMOTE,
                reason = "approved_remote_decision_fallback",
            )
        }

        return ReasoningSelection(
            tier = ReasoningTier.GENERATIVE,
            reason = if (availability.remoteSystemOne && !availability.remoteSystemOneAllowed) {
                "remote_decision_backend_not_authorized"
            } else {
                "decision_backend_unavailable"
            },
        )
    }
}
