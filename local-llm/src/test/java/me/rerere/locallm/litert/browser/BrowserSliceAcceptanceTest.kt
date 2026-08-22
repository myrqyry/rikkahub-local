package me.rerere.locallm.litert.browser

import me.rerere.locallm.litert.CapabilityGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSliceAcceptanceTest {

    private fun session(): BrowserSession = BrowserSession.create("s1")

    @Test
    fun `navigate flow produces navigation and ready state`() {
        val s = session()
        assertEquals(BrowserSession.State.READY, s.state)

        val started = s.dispatch(BrowserCommand.Navigate("https://example.com"))
        assertEquals(listOf<BrowserObservation>(BrowserObservation.NavigationStarted("https://example.com")), started)
        assertEquals(BrowserSession.State.NAVIGATING, s.state)

        val completed = s.observeNavigationCompleted("https://example.com")
        assertEquals(listOf<BrowserObservation>(BrowserObservation.NavigationCompleted("https://example.com")), completed)
        assertEquals(BrowserSession.State.READY, s.state)
    }

    @Test
    fun `snapshot is allowed in ready state`() {
        val s = session()
        val observations = s.dispatch(BrowserCommand.Snapshot)
        assertEquals(listOf<BrowserObservation>(BrowserObservation.SnapshotCaptured), observations)
        assertEquals(BrowserSession.State.READY, s.state)
    }

    @Test
    fun `click in navigating state is refused`() {
        val s = session()
        s.dispatch(BrowserCommand.Navigate("https://example.com"))
        val observations = s.dispatch(BrowserCommand.Click("#a"))
        assertEquals(1, observations.size)
        assertTrue(observations.single() is BrowserObservation.CommandRefused)
        assertEquals(BrowserSession.State.NAVIGATING, s.state)
    }

    @Test
    fun `unsafe url is refused by the gate`() {
        val s = session()
        val observations = s.dispatch(BrowserCommand.Navigate("javascript:alert(1)"))
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.CommandRefused("browser_navigation_denied")),
            observations,
        )
        assertEquals(BrowserSession.State.READY, s.state)
    }

    @Test
    fun `eval js is denied without a grant`() {
        val s = session()
        val observations = s.dispatch(BrowserCommand.EvalJs("document.title"))
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.CommandRefused("browser_eval_js_denied")),
            observations,
        )
        assertEquals(BrowserSession.State.READY, s.state)
    }

    @Test
    fun `eval js allowed with a grant`() {
        val s = session()
        val grant = CapabilityGrant(
            requestedCapabilities = listOf("browser_eval_js"),
            grantedCapabilities = listOf("browser_eval_js"),
            rejectedCapabilities = emptyList(),
        )
        val observations = s.dispatch(BrowserCommand.EvalJs("document.title"), granted = grant)
        assertEquals(listOf<BrowserObservation>(BrowserObservation.ActionAcknowledged), observations)
        assertEquals(BrowserSession.State.READY, s.state)
    }

    @Test
    fun `done terminates the session`() {
        val s = session()
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.ActionAcknowledged),
            s.dispatch(BrowserCommand.Done),
        )
        assertEquals(BrowserSession.State.DONE, s.state)
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.CommandRefused("session closed")),
            s.dispatch(BrowserCommand.Click("#a")),
        )
    }

    @Test
    fun `close evicts and emits session evicted`() {
        val s = session()
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.SessionEvicted),
            s.close(),
        )
        assertEquals(BrowserSession.State.EVICTED, s.state)
        assertEquals(emptyList<BrowserObservation>(), s.close())
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.CommandRefused("session closed")),
            s.dispatch(BrowserCommand.Snapshot),
        )
    }

    @Test
    fun `navigation failure transitions back to ready`() {
        val s = session()
        s.dispatch(BrowserCommand.Navigate("https://example.com"))
        val failed = s.observeNavigationFailed("https://example.com", detail = "connection refused")
        assertEquals(
            listOf<BrowserObservation>(BrowserObservation.PageError("https://example.com", "connection refused")),
            failed,
        )
        assertEquals(BrowserSession.State.READY, s.state)
    }
}
