package me.rerere.rikkahub.data.ai.tools.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPostconditionTest {
    @Test
    fun `visible text and expected package must both match`() {
        val condition = UiPostcondition(expectedText = "Connected", expectedPackage = "com.example.settings")
        assertTrue(condition.matches(UiObservedState("com.example.settings", setOf("Connected", "Wi-Fi"))))
        assertFalse(condition.matches(UiObservedState("com.other.app", setOf("Connected"))))
        assertFalse(condition.matches(UiObservedState("com.example.settings", setOf("Connecting"))))
    }

    @Test
    fun `empty condition never verifies an arbitrary screen`() {
        assertFalse(UiPostcondition().matches(UiObservedState("com.example", setOf("Ready"))))
    }

    @Test
    fun `absence only passes when an actual window was observed`() {
        val condition = UiPostcondition(absentText = "Loading")
        assertFalse(condition.matches(null))
        assertFalse(condition.matches(UiObservedState("com.example", setOf("Loading"))))
        assertTrue(condition.matches(UiObservedState("com.example", setOf("Ready"))))
    }

    @Test
    fun `preexisting result cannot be claimed as verified by a click`() {
        assertEquals(UiVerificationStatus.ALREADY_SATISFIED,
            assessUiPostcondition(actionAccepted = true, initiallySatisfied = true, observedSatisfied = true))
        assertEquals(UiVerificationStatus.PASSED,
            assessUiPostcondition(actionAccepted = true, initiallySatisfied = false, observedSatisfied = true))
    }

    @Test
    fun `rejected action and unobserved effect have distinct outcomes`() {
        assertEquals(UiVerificationStatus.ACTION_REJECTED,
            assessUiPostcondition(actionAccepted = false, initiallySatisfied = false, observedSatisfied = true))
        assertEquals(UiVerificationStatus.NOT_OBSERVED,
            assessUiPostcondition(actionAccepted = true, initiallySatisfied = false, observedSatisfied = false))
        assertEquals(UiVerificationStatus.NOT_OBSERVED,
            assessUiPostcondition(actionAccepted = true, initiallySatisfied = true, observedSatisfied = false))
    }
}
