package me.rerere.locallm.litert.mesh

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * A lightweight, deterministic event mesh for decoupled micro-agent communication.
 *
 * Micro-agents publish typed [MicroAgentEvent]s on named topics; subscribers register
 * per agent id and receive only the events they subscribed to. Delivery order is
 * deterministic (subscriber agent ids sorted), and a failing subscriber never blocks
 * or kills a publish for the remaining subscribers. Publishing never invokes tools —
 * it only routes events to in-process handlers.
 *
 * The [MicroAgentEventSink] seam forwards every published event to an external mesh /
 * audit layer (wired via DI in the app module, mirroring WorkflowReceiptSink).
 */
@Serializable
data class MicroAgentEvent(
    val sourceAgentId: String,
    val topic: String,
    val payload: JsonObject = buildJsonObject {},
    val atMs: Long = 0L,
)

data class EventDelivery(
    val event: MicroAgentEvent,
    val deliveredTo: List<String>,
)

fun interface MicroAgentEventSink {
    suspend fun publish(event: MicroAgentEvent)
}

class MicroAgentEventMesh(
    private val sink: MicroAgentEventSink? = null,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private class Subscription(
        val agentId: String,
        val topics: Set<String>,
        val onEvent: suspend (MicroAgentEvent) -> Unit,
    )

    private val mutex = Mutex()
    private val subscriptions = LinkedHashMap<String, Subscription>()

    /**
     * Register [onEvent] for [agentId]. With no [topics], the agent subscribes to all topics
     * (wildcard). Re-subscribing an existing agent id replaces its previous subscription.
     */
    suspend fun subscribe(agentId: String, vararg topics: String, onEvent: suspend (MicroAgentEvent) -> Unit) {
        mutex.withLock {
            subscriptions[agentId] = Subscription(agentId, topics.toSet(), onEvent)
        }
    }

    suspend fun unsubscribe(agentId: String) {
        mutex.withLock { subscriptions.remove(agentId) }
    }

    /**
     * Publish [event]. Stamps [MicroAgentEvent.atMs] with [nowMs] when the caller left it at 0.
     * Subscribers are matched by topic (wildcard matches all) and invoked in sorted agent-id
     * order. A throwing handler is isolated — other subscribers still receive the event.
     * The event is then forwarded to the optional [sink].
     */
    suspend fun publish(event: MicroAgentEvent): EventDelivery {
        val stamped = if (event.atMs > 0L) event else event.copy(atMs = nowMs())
        val snapshot = mutex.withLock { subscriptions.values.toList() }
        val matches = snapshot
            .filter { it.topics.isEmpty() || it.topics.contains(stamped.topic) }
            .sortedBy { it.agentId }

        val deliveredTo = mutableListOf<String>()
        matches.forEach { sub ->
            runCatching { sub.onEvent(stamped) }.onSuccess { deliveredTo += sub.agentId }
        }
        sink?.let { runCatching { it.publish(stamped) } }

        return EventDelivery(stamped, deliveredTo)
    }

    fun subscriberCount(): Int = subscriptions.size
}
