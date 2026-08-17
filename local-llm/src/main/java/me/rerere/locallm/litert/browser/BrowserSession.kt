package me.rerere.locallm.litert.browser

import me.rerere.locallm.litert.CapabilityGrant

/**
 * Deterministic browser session state machine. Pure Kotlin — no WebView, no
 * browser backend. The Android WebView pool becomes an adapter in a later phase.
 */
class BrowserSession private constructor(val ref: BrowserRef, private val gate: BrowserGate) {

    enum class State { READY, NAVIGATING, DONE, EVICTED }

    @Volatile
    private var currentState: State = State.READY

    val state: State get() = currentState

    /** Whether [state] is a terminal state (no further commands are accepted). */
    val isClosed: Boolean get() = currentState == State.DONE || currentState == State.EVICTED

    /**
     * Evaluate [command] against the gate and, if allowed, apply it to the
     * state machine. Refusals are returned as observations and never throw.
     */
    fun dispatch(command: BrowserCommand, granted: CapabilityGrant? = null): List<BrowserObservation> {
        if (isClosed) return listOf(BrowserObservation.CommandRefused("session closed"))

        val decision = gate.evaluate(command, granted)
        if (!decision.allowed) {
            return listOf(BrowserObservation.CommandRefused(decision.reason ?: "command_denied"))
        }

        return when (command) {
            is BrowserCommand.Navigate -> {
                if (currentState == State.READY || currentState == State.NAVIGATING) {
                    currentState = State.NAVIGATING
                    listOf(BrowserObservation.NavigationStarted(command.url))
                } else {
                    listOf(BrowserObservation.CommandRefused("navigate not valid in ${currentState.name}"))
                }
            }
            is BrowserCommand.Snapshot -> {
                if (currentState == State.READY) listOf(BrowserObservation.SnapshotCaptured)
                else listOf(BrowserObservation.CommandRefused("snapshot not valid in ${currentState.name}"))
            }
            is BrowserCommand.Done -> {
                currentState = State.DONE
                listOf(BrowserObservation.ActionAcknowledged)
            }
            is BrowserCommand.WaitFor -> {
                if (currentState == State.READY || currentState == State.NAVIGATING) {
                    listOf(BrowserObservation.ActionAcknowledged)
                } else {
                    listOf(BrowserObservation.CommandRefused("wait_for not valid in ${currentState.name}"))
                }
            }
            is BrowserCommand.EvalJs -> {
                if (currentState == State.READY) {
                    listOf(BrowserObservation.ActionAcknowledged)
                } else {
                    listOf(BrowserObservation.CommandRefused("eval_js not valid in ${currentState.name}"))
                }
            }
            is BrowserCommand.Click,
            is BrowserCommand.Type,
            is BrowserCommand.Scroll,
            is BrowserCommand.Back,
            is BrowserCommand.Forward,
            is BrowserCommand.Submit,
            is BrowserCommand.Select,
            -> {
                if (currentState == State.READY) listOf(BrowserObservation.ActionAcknowledged)
                else listOf(BrowserObservation.CommandRefused("${command::class.simpleName} not valid in ${currentState.name}"))
            }
        }
    }

    /** Backend reports a navigation completed. Only valid while NAVIGATING. */
    fun observeNavigationCompleted(url: String): List<BrowserObservation> {
        if (currentState != State.NAVIGATING) return emptyList()
        currentState = State.READY
        return listOf(BrowserObservation.NavigationCompleted(url))
    }

    /** Backend reports a navigation failure. Only valid while NAVIGATING. */
    fun observeNavigationFailed(url: String, detail: String? = null): List<BrowserObservation> {
        if (currentState != State.NAVIGATING) return emptyList()
        currentState = State.READY
        return listOf(BrowserObservation.PageError(url, detail))
    }

    /** Evict the session. Idempotent; emits SessionEvicted only once, unless already DONE. */
    fun close(): List<BrowserObservation> {
        if (currentState == State.DONE) return emptyList()
        if (currentState == State.EVICTED) return emptyList()
        currentState = State.EVICTED
        return listOf(BrowserObservation.SessionEvicted)
    }

    companion object {
        fun create(id: String, gate: BrowserGate = BrowserGate()): BrowserSession =
            BrowserSession(BrowserRef(id), gate)
    }
}
