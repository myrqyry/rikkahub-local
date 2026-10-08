package me.rerere.locallm.litert

import java.util.concurrent.atomic.AtomicInteger

/**
 * Arbitrates a LiteRT-LM generation callback against collector cancellation
 * and the user's explicit Stop action. Native callbacks may arrive on a
 * different thread after the Flow collector has already gone away.
 *
 * Exactly one native terminal callback can claim completion. A cancelled
 * turn must never be reported as a successful warm-KV-cache turn.
 */
internal class NativeTurnGate {
    private val phase = AtomicInteger(ACTIVE)

    val acceptsOutput: Boolean get() = phase.get() == ACTIVE

    /** Includes turns whose cancellation was already acknowledged by native code. */
    val wasCancelled: Boolean
        get() = phase.get() == CANCELLING || phase.get() == FINISHED_CANCELLED

    /** Returns true only for the first stop request on an active turn. */
    fun requestCancellation(): Boolean = phase.compareAndSet(ACTIVE, CANCELLING)

    /**
     * Called once per native onDone/onError callback. Returns whether this was
     * the first terminal callback, and whether the turn was already cancelled.
     */
    fun nativeTerminal(): Terminal {
        while (true) {
            when (val prior = phase.get()) {
                ACTIVE -> if (phase.compareAndSet(prior, FINISHED_ACTIVE)) return Terminal.Active
                CANCELLING -> if (phase.compareAndSet(prior, FINISHED_CANCELLED)) return Terminal.Cancelled
                else -> return Terminal.Duplicate
            }
        }
    }

    enum class Terminal { Active, Cancelled, Duplicate }

    private companion object {
        const val ACTIVE = 0
        const val CANCELLING = 1
        const val FINISHED_ACTIVE = 2
        const val FINISHED_CANCELLED = 3
    }
}
