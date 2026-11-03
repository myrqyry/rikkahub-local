package me.rerere.locallm.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SystemOneDecisionTest {
    @Test
    fun `deterministic result always wins`() {
        val selection = ReasoningRoutingPolicy.select(
            deterministicResolved = true,
            workKind = ReasoningWorkKind.BOOLEAN_DECISION,
            availability = ReasoningAvailability(
                localSystemOne = true,
                remoteSystemOne = true,
                remoteSystemOneAllowed = true,
            ),
        )

        assertEquals(ReasoningTier.DETERMINISTIC, selection.tier)
        assertEquals("deterministic_path_resolved", selection.reason)
    }

    @Test
    fun `local system one is primary fuzzy decision layer`() {
        val selection = ReasoningRoutingPolicy.select(
            deterministicResolved = false,
            workKind = ReasoningWorkKind.CHOICE_DECISION,
            availability = ReasoningAvailability(
                localSystemOne = true,
                remoteSystemOne = true,
                remoteSystemOneAllowed = true,
            ),
        )

        assertEquals(ReasoningTier.SYSTEM_ONE_LOCAL, selection.tier)
    }

    @Test
    fun `approved remote system one is fallback not primary`() {
        val selection = ReasoningRoutingPolicy.select(
            deterministicResolved = false,
            workKind = ReasoningWorkKind.SCORE_DECISION,
            availability = ReasoningAvailability(
                localSystemOne = false,
                remoteSystemOne = true,
                remoteSystemOneAllowed = true,
            ),
        )

        assertEquals(ReasoningTier.SYSTEM_ONE_REMOTE, selection.tier)
        assertEquals("approved_remote_decision_fallback", selection.reason)
    }

    @Test
    fun `remote decision backend cannot be used without explicit authorization`() {
        val selection = ReasoningRoutingPolicy.select(
            deterministicResolved = false,
            workKind = ReasoningWorkKind.BOOLEAN_DECISION,
            availability = ReasoningAvailability(
                localSystemOne = false,
                remoteSystemOne = true,
                remoteSystemOneAllowed = false,
            ),
        )

        assertEquals(ReasoningTier.GENERATIVE, selection.tier)
        assertEquals("remote_decision_backend_not_authorized", selection.reason)
    }

    @Test
    fun `synthesis goes to generative reasoning even when system one is available`() {
        val selection = ReasoningRoutingPolicy.select(
            deterministicResolved = false,
            workKind = ReasoningWorkKind.SYNTHESIS,
            availability = ReasoningAvailability(localSystemOne = true),
        )

        assertEquals(ReasoningTier.GENERATIVE, selection.tier)
        assertEquals("synthesis_required", selection.reason)
    }

    @Test
    fun `typed question invariants reject malformed choice and score shapes`() {
        assertThrows(IllegalArgumentException::class.java) {
            DecisionQuestion.Choice(
                id = "route",
                instructions = "Choose a route.",
                options = listOf("same", "same"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DecisionQuestion.Score(
                id = "quality",
                instructions = "Score quality.",
                min = 5,
                max = 5,
            )
        }
    }

    @Test
    fun `probabilities must stay bounded`() {
        assertThrows(IllegalArgumentException::class.java) {
            DecisionAnswer.Boolean(probability = 1.1)
        }
    }

    @Test
    fun `request question ids must be unique`() {
        assertThrows(IllegalArgumentException::class.java) {
            DecisionRequest(
                state = "state",
                questions = listOf(
                    DecisionQuestion.Boolean("same", "First."),
                    DecisionQuestion.Boolean("same", "Second."),
                ),
            )
        }
    }

    @Test
    fun `result validation accepts answers inside the declared request domains`() {
        val request = DecisionRequest(
            state = "state",
            questions = listOf(
                DecisionQuestion.Boolean("continue", "Continue?"),
                DecisionQuestion.Choice("route", "Choose.", listOf("local", "remote")),
                DecisionQuestion.Score("quality", "Score.", min = 1, max = 5),
            ),
        )
        val result = DecisionResult(
            answers = mapOf(
                "continue" to DecisionAnswer.Boolean(0.8),
                "route" to DecisionAnswer.Choice(
                    value = "local",
                    probabilities = mapOf("local" to 0.8, "remote" to 0.2),
                ),
                "quality" to DecisionAnswer.Score(
                    value = 4,
                    probabilities = mapOf(3 to 0.1, 4 to 0.8, 5 to 0.1),
                ),
            ),
            provider = "test",
            latencyMs = 12,
        )

        assertEquals(result, result.validateAgainst(request))
    }

    @Test
    fun `result validation rejects missing extra and mismatched answers`() {
        val request = DecisionRequest(
            state = "state",
            questions = listOf(
                DecisionQuestion.Boolean("continue", "Continue?"),
                DecisionQuestion.Choice("route", "Choose.", listOf("local", "remote")),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            DecisionResult(
                answers = mapOf("continue" to DecisionAnswer.Boolean(0.5)),
                provider = "test",
                latencyMs = 1,
            ).validateAgainst(request)
        }

        assertThrows(IllegalArgumentException::class.java) {
            DecisionResult(
                answers = mapOf(
                    "continue" to DecisionAnswer.Boolean(0.5),
                    "route" to DecisionAnswer.Choice("local"),
                    "extra" to DecisionAnswer.Boolean(0.5),
                ),
                provider = "test",
                latencyMs = 1,
            ).validateAgainst(request)
        }

        assertThrows(IllegalArgumentException::class.java) {
            DecisionResult(
                answers = mapOf(
                    "continue" to DecisionAnswer.Score(1),
                    "route" to DecisionAnswer.Choice("local"),
                ),
                provider = "test",
                latencyMs = 1,
            ).validateAgainst(request)
        }
    }

    @Test
    fun `result validation rejects values outside choice and score domains`() {
        val choiceRequest = DecisionRequest(
            state = "state",
            questions = listOf(
                DecisionQuestion.Choice("route", "Choose.", listOf("local", "remote")),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            DecisionResult(
                answers = mapOf(
                    "route" to DecisionAnswer.Choice(
                        value = "cloud",
                        probabilities = mapOf("cloud" to 1.0),
                    ),
                ),
                provider = "test",
                latencyMs = 1,
            ).validateAgainst(choiceRequest)
        }

        val scoreRequest = DecisionRequest(
            state = "state",
            questions = listOf(
                DecisionQuestion.Score("quality", "Score.", min = 1, max = 5),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            DecisionResult(
                answers = mapOf(
                    "quality" to DecisionAnswer.Score(
                        value = 6,
                        probabilities = mapOf(6 to 1.0),
                    ),
                ),
                provider = "test",
                latencyMs = 1,
            ).validateAgainst(scoreRequest)
        }
    }

    @Test
    fun `result metadata must be usable`() {
        assertThrows(IllegalArgumentException::class.java) {
            DecisionResult(
                answers = emptyMap(),
                provider = " ",
                latencyMs = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DecisionResult(
                answers = emptyMap(),
                provider = "test",
                latencyMs = -1,
            )
        }
    }
}
