package me.rerere.locallm.litert.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * A lightweight, deterministic event mesh for decoupled micro-agent communication.
 *
 * Micro-agents publish typed [MicroAgentEvent]s on canonical [MicroAgentTopics]. Publishing
 * never invokes tools and never grants authority — it only routes events to in-process
 * subscribers and forwards them to the audit [sink]. Deterministic subscriber ordering
 * (agent ids sorted) and per-subscriber exception isolation are preserved.
 *
 * Roadmap D: the envelope is hardened with event/correlation/causation identity, an AgentRun
 * evidence boundary, hop limiting, a dedupe identity, and an optional deadline. Subscribers
 * get a bounded [Channel] inbox (overflow = reject) drained by a single worker coroutine, so
 * handlers may safely contain model generation or execution without the original inline
 * delivery. Cancellation is correlation-scoped: cancelled orchestrations stop being delivered.
 */
@Serializable
data class MicroAgentEvent(
    val sourceAgentId: String,
    val topic: String,
    val payload: JsonObject = buildJsonObject {},
    val atMs: Long = 0L,

    val eventId: String,
    val correlationId: String,
    val causationId: String? = null,
    val runId: String? = null,

    val hopCount: Int = 0,
    val maxHops: Int = DEFAULT_MAX_HOPS,

    val dedupeKey: String? = null,
    val deadlineAtMs: Long? = null,
) {
    companion object {
        const val DEFAULT_MAX_HOPS = 8
    }
}

/**
 * The outcome of [MicroAgentEventMesh.publish]. Exposes typed results rather than silently
 * ignoring malformed, duplicate, or expired events.
 */
sealed interface MeshPublishResult {
    data class Delivered(val delivery: EventDelivery) : MeshPublishResult

    data class Duplicate(val eventId: String, val reason: String) : MeshPublishResult

    data class HopLimitExceeded(val eventId: String, val hopCount: Int, val maxHops: Int) : MeshPublishResult

    data class Expired(val eventId: String, val deadlineAtMs: Long) : MeshPublishResult

    data class Rejected(val eventId: String, val reason: String) : MeshPublishResult
}

data class EventDelivery(
    val event: MicroAgentEvent,
    val deliveredTo: List<String>,
    /** Subscribers whose bounded inbox was full — a traceable AGENT_QUEUE_FULL, never a silent drop. */
    val queueOverflow: List<String> = emptyList(),
)

fun interface MicroAgentEventSink {
    suspend fun publish(event: MicroAgentEvent)
}

/**
 * Transport for micro-agent coordination.
 *
 * @param scope coroutine scope that owns each subscriber's inbox-draining worker.
 * @param sink optional audit seam; every accepted event is forwarded here (AGENT_EVENT_* ledger).
 * @param nowMs injectable clock for deterministic tests.
 * @param defaultCapacity per-subscriber inbox capacity when not overridden at subscribe time.
 * @param dedupeRetentionMs how long seen event ids / dedupe keys are remembered.
 */
class MicroAgentEventMesh(
    private val scope: CoroutineScope,
    private val sink: MicroAgentEventSink? = null,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val defaultCapacity: Int = 16,
    private val dedupeRetentionMs: Long = 60_000L,
) {
    private class Subscription(
        val agentId: String,
        val topics: Set<String>,
        val channel: Channel<MicroAgentEvent>,
        val worker: Job,
    )

    private val mutex = Mutex()
    private val subscriptions = LinkedHashMap<String, Subscription>()
    private val seenEventIds = HashMap<String, Long>()
    private val seenDedupeKeys = HashMap<String, Long>()
    private val cancelledCorrelations = HashSet<String>()

    /**
     * Register [onEvent] for [agentId]. With no [topics], the agent subscribes to all topics
     * (wildcard). Re-subscribing an existing agent id replaces its previous subscription and
     * cancels the old worker. [capacity] bounds the inbox (default [defaultCapacity]);
     * overflow rejects with AGENT_QUEUE_FULL.
     */
    suspend fun subscribe(
        agentId: String,
        vararg topics: String,
        capacity: Int = defaultCapacity,
        onEvent: suspend (MicroAgentEvent) -> Unit,
    ) {
        mutex.withLock {
            subscriptions.remove(agentId)?.worker?.cancel()
            val channel = Channel<MicroAgentEvent>(capacity)
            val worker = scope.launch {
                for (event in channel) {
                    try {
                        onEvent(event)
                    } catch (t: Throwable) {
                        // Subscriber exceptions are isolated; the mesh stays alive.
                    }
                }
            }
            subscriptions[agentId] = Subscription(agentId, topics.toSet(), channel, worker)
        }
    }

    suspend fun unsubscribe(agentId: String) {
        mutex.withLock {
            subscriptions.remove(agentId)?.worker?.cancel()
        }
    }

    /**
     * Cancels a whole orchestration by [correlationId]: queued events for that correlation are
     * no longer delivered (still forwarded to the sink as evidence), and the active role's
     * [onEvent] will observe cancellation via [isCancelled].
     */
    suspend fun cancel(correlationId: String) {
        mutex.withLock { cancelledCorrelations.add(correlationId) }
    }

    suspend fun isCancelled(correlationId: String): Boolean = mutex.withLock {
        correlationId in cancelledCorrelations
    }

    /**
     * Publish [event]. Validates envelope identity, hop budget, dedupe and deadline before
     * routing. Rejects (typed [MeshPublishResult]) a blank topic/source/event id, a hop count
     * over the max, an already-seen event id or dedupe key, or an expired deadline.
     */
    suspend fun publish(event: MicroAgentEvent): MeshPublishResult {
        val id = event.eventId
        if (event.topic.isBlank()) return MeshPublishResult.Rejected(id, "blank topic")
        if (event.sourceAgentId.isBlank()) return MeshPublishResult.Rejected(id, "blank source agent id")
        if (id.isBlank()) return MeshPublishResult.Rejected(id, "blank event id")
        if (event.hopCount > event.maxHops) {
            return MeshPublishResult.HopLimitExceeded(id, event.hopCount, event.maxHops)
        }
        val now = nowMs()
        val deadline = event.deadlineAtMs
        if (deadline != null && now >= deadline) return MeshPublishResult.Expired(id, deadline)

        val stamped = if (event.atMs > 0L) event else event.copy(atMs = now)

        mutex.withLock {
            purgeExpired(now)
            if (seenEventIds.containsKey(stamped.eventId)) {
                return MeshPublishResult.Duplicate(stamped.eventId, "event id already seen")
            }
            val dk = stamped.dedupeKey
            if (dk != null && seenDedupeKeys.containsKey(dk)) {
                return MeshPublishResult.Duplicate(stamped.eventId, "dedupe key already seen: $dk")
            }
            seenEventIds[stamped.eventId] = now
            if (dk != null) seenDedupeKeys[dk] = now
        }

        // Correlation cancelled → record evidence, deliver to nobody.
        if (mutex.withLock { stamped.correlationId in cancelledCorrelations }) {
            sink?.let { runCatching { it.publish(stamped) } }
            return MeshPublishResult.Delivered(EventDelivery(stamped, emptyList()))
        }

        val snapshot = mutex.withLock { subscriptions.values.toList() }
        val matches = snapshot
            .filter { it.topics.isEmpty() || it.topics.contains(stamped.topic) }
            .sortedBy { it.agentId }

        val deliveredTo = mutableListOf<String>()
        val overflowed = mutableListOf<String>()
        matches.forEach { sub ->
            if (sub.channel.trySend(stamped).isSuccess) {
                deliveredTo += sub.agentId
            } else {
                overflowed += sub.agentId
            }
        }

        sink?.let { runCatching { it.publish(stamped) } }

        return MeshPublishResult.Delivered(EventDelivery(stamped, deliveredTo, overflowed))
    }

    suspend fun subscriberCount(): Int = mutex.withLock { subscriptions.size }

    private fun purgeExpired(now: Long) {
        val cutoff = now - dedupeRetentionMs
        seenEventIds.entries.removeAll { it.value < cutoff }
        seenDedupeKeys.entries.removeAll { it.value < cutoff }
    }
}
