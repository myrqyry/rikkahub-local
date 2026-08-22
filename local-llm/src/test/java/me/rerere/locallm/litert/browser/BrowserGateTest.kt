package me.rerere.locallm.litert.browser

import me.rerere.locallm.litert.CapabilityGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserGateTest {

    private val gate = BrowserGate()

    @Test
    fun `navigate to an http url is allowed`() {
        val decision = gate.evaluate(BrowserCommand.Navigate("https://example.com"), granted = null)
        assertTrue(decision.allowed)
        assertEquals(BrowserEffect.NAVIGATE, decision.effect)
    }

    @Test
    fun `navigate to an unsafe scheme is refused`() {
        val decision = gate.evaluate(BrowserCommand.Navigate("javascript:alert(1)"), granted = null)
        assertFalse(decision.allowed)
        assertEquals("browser_navigation_denied", decision.reason)
    }

    @Test
    fun `navigate to a file scheme is allowed`() {
        val decision = gate.evaluate(BrowserCommand.Navigate("content://media/image/1"), granted = null)
        assertTrue(decision.allowed)
    }

    @Test
    fun `eval js is denied without a grant`() {
        val decision = gate.evaluate(BrowserCommand.EvalJs("document.title"), granted = null)
        assertFalse(decision.allowed)
        assertEquals("browser_eval_js_denied", decision.reason)
        assertNull(decision.effect)
    }

    @Test
    fun `eval js is allowed with an eval grant`() {
        val grant = CapabilityGrant(
            requestedCapabilities = listOf("browser_eval_js"),
            grantedCapabilities = listOf("browser_eval_js"),
            rejectedCapabilities = emptyList(),
        )
        val decision = gate.evaluate(BrowserCommand.EvalJs("document.title"), granted = grant)
        assertTrue(decision.allowed)
        assertEquals(BrowserEffect.EVAL_JS, decision.effect)
    }

    @Test
    fun `interactive commands are allowed with no grant`() {
        assertTrue(gate.evaluate(BrowserCommand.Click("#a"), granted = null).allowed)
        assertTrue(gate.evaluate(BrowserCommand.Type("#a", "x"), granted = null).allowed)
        assertTrue(gate.evaluate(BrowserCommand.Snapshot, granted = null).allowed)
        assertTrue(gate.evaluate(BrowserCommand.Done, granted = null).allowed)
    }
}
