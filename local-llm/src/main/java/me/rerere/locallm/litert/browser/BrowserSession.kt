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

    internal val ledgerCommands = mutableListOf<String>()
    internal val ledgerEffects = mutableSetOf<BrowserEffect>()
    internal val ledgerRefusals = mutableListOf<String>()

    val state: State get() = currentState

    /** Whether [state] is a terminal state (no further commands are accepted). */
    val isClosed: Boolean get() = currentState == State.DONE || currentState == State.EVICTED

    /**
     * Evaluate [command] against the gate and, if allowed, apply it to the
     * state machine. Refusals are returned as observations and never throw.
     *
     * Ledger rule: gate → validate current state → commit transition/effect →
     * record observation; otherwise record a refusal. Effects are only ever
     * recorded for transitions that are actually accepted, so receipts cannot
     * claim an effect that the state machine refused.
     */
    fun dispatch(command: BrowserCommand, granted: CapabilityGrant? = null): List<BrowserObservation> {
        val commandName = command::class.simpleName.orEmpty()
        if (isClosed) {
            val refusal = BrowserObservation.CommandRefused("session closed")
            ledgerCommands += commandName
            ledgerRefusals += refusal.reason
            return listOf(refusal)
        }

        val decision = gate.evaluate(command, granted)
        if (!decision.allowed) {
            val refusal = BrowserObservation.CommandRefused(decision.reason ?: "command_denied")
            ledgerCommands += commandName
            ledgerRefusals += refusal.reason
            return listOf(refusal)
        }

        fun commit(observation: BrowserObservation): List<BrowserObservation> {
            ledgerCommands += commandName
            decision.effect?.let { ledgerEffects += it }
            return listOf(observation)
        }

        fun refuse(reason: String): List<BrowserObservation> {
            ledgerCommands += commandName
            ledgerRefusals += reason
            return listOf(BrowserObservation.CommandRefused(reason))
        }

        return when (command) {
            is BrowserCommand.Navigate -> {
                if (currentState == State.READY || currentState == State.NAVIGATING) {
                    currentState = State.NAVIGATING
                    commit(BrowserObservation.NavigationStarted(command.url))
                } else {
                    refuse("navigate not valid in ${currentState.name}")
                }
            }
            is BrowserCommand.Snapshot -> {
                if (currentState == State.READY) commit(BrowserObservation.SnapshotCaptured)
                else refuse("snapshot not valid in ${currentState.name}")
            }
            is BrowserCommand.Done -> {
                currentState = State.DONE
                commit(BrowserObservation.ActionAcknowledged)
            }
            is BrowserCommand.WaitFor -> {
                if (currentState == State.READY || currentState == State.NAVIGATING) {
                    commit(BrowserObservation.ActionAcknowledged)
                } else {
                    refuse("wait_for not valid in ${currentState.name}")
                }
            }
            is BrowserCommand.EvalJs -> {
                if (currentState == State.READY) commit(BrowserObservation.ActionAcknowledged)
                else refuse("eval_js not valid in ${currentState.name}")
            }
            is BrowserCommand.Click,
            is BrowserCommand.Type,
            is BrowserCommand.Scroll,
            is BrowserCommand.Back,
            is BrowserCommand.Forward,
            is BrowserCommand.Submit,
            is BrowserCommand.Select,
            -> {
                if (currentState == State.READY) {
                    commit(BrowserObservation.ActionAcknowledged)
                } else {
                    refuse("${command::class.simpleName} not valid in ${currentState.name}")
                }
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
