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
}
