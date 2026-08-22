package me.rerere.locallm.litert.browser

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserReceiptTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `receipt correlates with a session run`() {
        val s = BrowserSession.create("s1")
        s.dispatch(BrowserCommand.Navigate("https://example.com"))
        s.observeNavigationCompleted("https://example.com")
        s.dispatch(BrowserCommand.Click("#a"))
        s.dispatch(BrowserCommand.Done)

        val receipt = s.buildReceipt(startedAtMs = 100L, error = null)

        assertEquals("browser:s1", receipt.session.toString())
        // type names in dispatch order
        assertEquals(listOf("Navigate", "Click", "Done"), receipt.commands)
        assertEquals(setOf(BrowserEffect.NAVIGATE, BrowserEffect.CLICK, BrowserEffect.DONE), receipt.effects)
        assertEquals(emptyList<String>(), receipt.refusals)
        assertEquals(3, receipt.observationCount)
        assertEquals(100L, receipt.startedAtMs)
        assertNull(receipt.completedAtMs)
        assertEquals("DONE", receipt.terminalState)
        assertNull(receipt.error)
    }

    @Test
    fun `receipt records refusals in order`() {
        val s = BrowserSession.create("s2")
        s.dispatch(BrowserCommand.Navigate("javascript:alert(1)"))
        s.dispatch(BrowserCommand.EvalJs("x"))
        s.close()

        val receipt = s.buildReceipt(startedAtMs = 5L)

        assertEquals(listOf("Navigate", "EvalJs"), receipt.commands)
        assertEquals(emptySet<BrowserEffect>(), receipt.effects)
        assertEquals(listOf("browser_navigation_denied", "browser_eval_js_denied"), receipt.refusals)
        assertEquals(2, receipt.observationCount)
        assertEquals("EVICTED", receipt.terminalState)
    }

    @Test
    fun `state-level refusals are recorded without committing effects`() {
        val s = BrowserSession.create("s4")
        s.dispatch(BrowserCommand.Navigate("https://example.com"))
        s.dispatch(BrowserCommand.Click("#a"))
        s.dispatch(BrowserCommand.Done)

        val receipt = s.buildReceipt(startedAtMs = 1L)

        assertEquals(listOf("Navigate", "Click", "Done"), receipt.commands)
        assertEquals(setOf(BrowserEffect.NAVIGATE, BrowserEffect.DONE), receipt.effects)
        assertEquals(listOf("Click not valid in NAVIGATING"), receipt.refusals)
    }

    @Test
    fun `receipt round-trips through serialization`() {
        val s = BrowserSession.create("s3")
        s.dispatch(BrowserCommand.Done)
        val receipt = s.buildReceipt(startedAtMs = 1L)
        assertEquals(receipt, json.decodeFromString(BrowserReceipt.serializer(), json.encodeToString(BrowserReceipt.serializer(), receipt)))
    }
}
