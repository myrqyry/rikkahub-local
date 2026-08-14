package me.rerere.locallm.litert.terminal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Phase F (roadmap F5). A typed event that describes something an agent observed about its
 * environment. [ObservationStream] owns environmental evidence and fans it out to subscribed
 * agents; it is shared later by BrowserSession / Services / Workspace. Events are immutable
 * snapshots of a moment in time ([atMs]).
 */
sealed interface ObservationEvent {
    val atMs: Long

    /** A process was accepted by the backend and began executing [command]. */
    data class ProcessStarted(
        val process: ProcessRef,
        val command: List<String>,
        override val atMs: Long,
    ) : ObservationEvent

    /** Raw output bytes from [process] on [stream]. */
    data class ProcessOutput(
        val process: ProcessRef,
        val stream: TerminalStream,
        val bytes: ByteArray,
        override val atMs: Long,
    ) : ObservationEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ProcessOutput) return false
            return process == other.process &&
                stream == other.stream &&
                atMs == other.atMs &&
                bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = process.hashCode()
            result = 31 * result + stream.hashCode()
            result = 31 * result + atMs.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    /** [process] terminated with [completion]. */
    data class ProcessExited(
        val process: ProcessRef,
        val completion: ProcessCompletion,
        override val atMs: Long,
    ) : ObservationEvent

    /** An [InputSource] owner acquired the write lease to [process]'s input. */
    data class TerminalInput(
        val process: ProcessRef,
        val owner: InputSource,
        override val atMs: Long,
    ) : ObservationEvent

    /** The workspace filesystem changed. */
    data class WorkspaceChanged(
        val workspaceId: String,
        override val atMs: Long,
    ) : ObservationEvent

    /** An unrecoverable observation-side failure (e.g. bounded-stream overflow). */
    data class RuntimeError(
        val message: String,
        override val atMs: Long,
    ) : ObservationEvent
}

/**
 * Phase F (roadmap F5). A bounded, ordered fan-out of [ObservationEvent] to subscribing agents.
 *
 * Delivery is deterministic: [emit] serializes on an internal mutex and each subscriber's
 * callback runs synchronously, in FIFO order. Per-subscriber capacity is bounded by [capacity];
 * when a subscriber is at capacity, [emit] best-effort drops the event and notifies that
 * subscriber with an [ObservationEvent.RuntimeError] instead. One throwing subscriber never
 * blocks or breaks another — every callback is exception-isolated.
 *
 * [scope] is the lifecycle owner under which future async fan-out / buffered consumers will
 * run (kept on the API for symmetry with other Phase F seams; synchronous delivery is used
 * today so behaviour is deterministic and trivially testable without a test dispatcher).
 */
class ObservationStream(
    private val scope: CoroutineScope,
    private val capacity: Int = 64,
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    /** Outcome of [subscribe]. */
    sealed interface SubscribeResult {
        data object Subscribed : SubscribeResult

        /** The agent is already subscribed or the stream rejected the request. */
        data class Rejected(val reason: String) : SubscribeResult
    }

    private data class Subscription(
        val onEvent: suspend (ObservationEvent) -> Unit,
        val seen: Int = 0,
    )

    private val subscriptions = ConcurrentHashMap<String, Subscription>()
    private val gate = Mutex()
    private val overflows = AtomicLong(0)

    /** Register [onEvent] to receive every event emitted while subscribed to [agentId]. */
    suspend fun subscribe(agentId: String, onEvent: suspend (ObservationEvent) -> Unit): SubscribeResult =
        gate.withLock {
            if (subscriptions.containsKey(agentId)) {
                SubscribeResult.Rejected("agent_already_subscribed:$agentId")
            } else {
                subscriptions[agentId] = Subscription(onEvent)
                SubscribeResult.Subscribed
            }
        }

    /**
     * Deliver [event] to every subscriber. Best-effort and deterministic: a subscriber at
     * [capacity] is skipped and notified with an overflow [ObservationEvent.RuntimeError].
     * Exceptions thrown by a subscriber are swallowed so the rest keep receiving.
     */
    suspend fun emit(event: ObservationEvent) {
        gate.withLock {
            for ((agentId, sub) in subscriptions.toMap()) {
                if (sub.seen >= capacity) {
                    overflows.incrementAndGet()
                    deliver(sub, ObservationEvent.RuntimeError("observation_overflow:$agentId", event.atMs))
                } else {
                    subscriptions[agentId] = sub.copy(seen = sub.seen + 1)
                    deliver(sub, event)
                }
            }
        }
    }

    private suspend fun deliver(sub: Subscription, event: ObservationEvent) {
        try {
            sub.onEvent(event)
        } catch (_: Throwable) {
            // Exception isolation: a broken subscriber must not prevent others from observing.
        }
    }

    /** Remove [agentId]'s subscription; further emits are not delivered to it. */
    suspend fun unsubscribe(agentId: String) {
        gate.withLock { subscriptions.remove(agentId) }
    }

    /** Number of currently-subscribed agents. */
    val subscriberCount: Int get() = subscriptions.size

    /** Number of events dropped because a subscriber was at capacity. */
    val overflowCount: Long get() = overflows.get()
}
