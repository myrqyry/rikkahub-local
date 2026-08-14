package me.rerere.locallm.litert.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class MicroAgentEventMeshTest {

    private var idSeq = 0L
    private fun nextId() = "evt-${idSeq++}"
    private fun nextCorr() = "corr-${idSeq++}"

    private fun event(
        source: String,
        topic: String,
        eventId: String = nextId(),
        correlationId: String = nextCorr(),
        atMs: Long = 0L,
        payload: JsonObject = buildJsonObject { put("k", source) },
        dedupeKey: String? = null,
        hopCount: Int = 0,
        maxHops: Int = MicroAgentEvent.DEFAULT_MAX_HOPS,
        deadlineAtMs: Long? = null,
    ) = MicroAgentEvent(
        sourceAgentId = source,
        topic = topic,
        payload = payload,
        atMs = atMs,
        eventId = eventId,
        correlationId = correlationId,
        dedupeKey = dedupeKey,
        hopCount = hopCount,
        maxHops = maxHops,
        deadlineAtMs = deadlineAtMs,
    )

    private fun delivered(result: MeshPublishResult): EventDelivery =
        (result as MeshPublishResult.Delivered).delivery

    private suspend fun awaitQueue(queue: ConcurrentLinkedQueue<String>, expected: Int, timeoutMs: Long = 2000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (queue.size < expected && System.currentTimeMillis() < deadline) delay(5)
        assertEquals(expected, queue.size)
    }

    @Test
    fun deliversOnlyToTopicMatchedSubscribers() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val received = ConcurrentLinkedQueue<String>()
        mesh.subscribe("a", "alpha") { received += it.sourceAgentId }
        mesh.subscribe("b", "beta") { received += it.sourceAgentId }
        val result = mesh.publish(event("src", "alpha"))
        assertEquals(listOf("a"), delivered(result).deliveredTo)
        awaitQueue(received, 1)
        assertEquals(listOf("src"), received.toList())
    }

    @Test
    fun wildcardSubscriberReceivesAllTopics() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val received = ConcurrentLinkedQueue<String>()
        mesh.subscribe("all") { received += it.topic }
        mesh.publish(event("src", "alpha"))
        mesh.publish(event("src", "beta"))
        awaitQueue(received, 2)
        assertEquals(listOf("alpha", "beta"), received.toList())
    }

    @Test
    fun deterministicOrderByAgentId() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val received = ConcurrentLinkedQueue<String>()
        mesh.subscribe("z") { received += it.sourceAgentId }
        mesh.subscribe("m") { received += it.sourceAgentId }
        mesh.subscribe("a") { received += it.sourceAgentId }
        mesh.publish(event("src", "alpha"))
        awaitQueue(received, 3)
        assertEquals(listOf("src", "src", "src"), received.toList())
        assertEquals(listOf("a", "m", "z"), delivered(mesh.publish(event("src2", "alpha"))).deliveredTo)
    }

    @Test
    fun failingSubscriberDoesNotBlockOthers() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val received = ConcurrentLinkedQueue<String>()
        mesh.subscribe("boom") { throw IllegalStateException("kaboom") }
        mesh.subscribe("ok") { received += it.sourceAgentId }
        val result = mesh.publish(event("src", "alpha"))
        assertEquals(listOf("boom", "ok"), delivered(result).deliveredTo)
        awaitQueue(received, 1)
        assertEquals(listOf("src"), received.toList())
    }

    @Test
    fun noSubscribersStillStampsAndForwardsToSink() = runBlocking {
        val scope = CoroutineScope(Job())
        val sunk = ConcurrentLinkedQueue<MicroAgentEvent>()
        val mesh = MicroAgentEventMesh(scope, sink = MicroAgentEventSink { sunk += it }, nowMs = { 1234L })
        val result = mesh.publish(event("src", "alpha", atMs = 0L))
        val delivery = delivered(result)
        assertEquals(emptyList<String>(), delivery.deliveredTo)
        assertEquals(1234L, delivery.event.atMs)
        awaitQueue2(sunk, 1)
        assertEquals(1234L, sunk.first().atMs)
    }

    private suspend fun awaitQueue2(q: ConcurrentLinkedQueue<*>, expected: Int, timeoutMs: Long = 2000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (q.size < expected && System.currentTimeMillis() < deadline) delay(5)
        assertEquals(expected, q.size)
    }

    @Test
    fun sinkReceivesEveryPublishedEvent() = runBlocking {
        val scope = CoroutineScope(Job())
        val sunk = ConcurrentLinkedQueue<String>()
        val mesh = MicroAgentEventMesh(scope, sink = MicroAgentEventSink { sunk += it.topic })
        mesh.publish(event("src", "t1"))
        mesh.publish(event("src", "t2"))
        awaitQueue(sunk, 2)
        assertEquals(listOf("t1", "t2"), sunk.toList())
    }

    @Test
    fun topicMatchingIsCaseSensitive() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val received = ConcurrentLinkedQueue<String>()
        mesh.subscribe("a", "Alpha") { received += it.topic }
        val result = mesh.publish(event("src", "alpha"))
        assertEquals(emptyList<String>(), delivered(result).deliveredTo)
    }

    @Test
    fun preservesCallerSuppliedTimestamp() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope, nowMs = { 1L })
        val result = mesh.publish(event("src", "alpha", atMs = 999L))
        assertEquals(999L, delivered(result).event.atMs)
    }

    @Test
    fun resubscribeReplacesExistingSubscription() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val received = ConcurrentLinkedQueue<String>()
        mesh.subscribe("a", "alpha") { received += "first" }
        mesh.subscribe("a", "beta") { received += "second" }
        mesh.publish(event("src", "beta"))
        awaitQueue(received, 1)
        assertEquals(listOf("second"), received.toList())
        assertEquals(1, mesh.subscriberCount())
    }

    @Test
    fun unsubscribeStopsDelivery() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val received = ConcurrentLinkedQueue<String>()
        mesh.subscribe("a", "alpha") { received += it.sourceAgentId }
        mesh.publish(event("src", "alpha"))
        awaitQueue(received, 1)
        mesh.unsubscribe("a")
        mesh.publish(event("src", "alpha"))
        assertEquals(1, received.size)
    }

    @Test
    fun duplicateEventIdRejected() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val received = ConcurrentLinkedQueue<String>()
        mesh.subscribe("a", "alpha") { received += it.sourceAgentId }
        val e = event("src", "alpha", eventId = "dup-1", correlationId = "c1")
        val first = mesh.publish(e)
        assertEquals(listOf("a"), delivered(first).deliveredTo)
        val second = mesh.publish(e)
        assertTrue(second is MeshPublishResult.Duplicate)
        awaitQueue(received, 1)
        assertEquals(1, received.size)
    }

    @Test
    fun duplicateDedupeKeyRejected() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val received = ConcurrentLinkedQueue<String>()
        mesh.subscribe("a", "alpha") { received += it.sourceAgentId }
        val first = mesh.publish(event("src", "alpha", eventId = "d1", correlationId = "c1", dedupeKey = "effect-write"))
        assertEquals(listOf("a"), delivered(first).deliveredTo)
        val dup = mesh.publish(event("src", "alpha", eventId = "d2", correlationId = "c2", dedupeKey = "effect-write"))
        assertTrue(dup is MeshPublishResult.Duplicate)
        awaitQueue(received, 1)
        assertEquals(1, received.size)
    }

    @Test
    fun hopLimitExceededRejected() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val result = mesh.publish(event("src", "alpha", hopCount = 9, maxHops = 8))
        assertTrue(result is MeshPublishResult.HopLimitExceeded)
    }

    @Test
    fun expiredDeadlineRejected() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope, nowMs = { 1000L })
        val result = mesh.publish(event("src", "alpha", deadlineAtMs = 999L))
        assertTrue(result is MeshPublishResult.Expired)
    }

    @Test
    fun blankTopicRejected() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val result = mesh.publish(event("src", "  "))
        assertTrue(result is MeshPublishResult.Rejected)
    }

    @Test
    fun cancelledCorrelationReceivesNoDeliveryButSinks() = runBlocking {
        val scope = CoroutineScope(Job())
        val sunk = ConcurrentLinkedQueue<String>()
        val mesh = MicroAgentEventMesh(scope, sink = MicroAgentEventSink { sunk += it.topic })
        val received = ConcurrentLinkedQueue<String>()
        mesh.subscribe("a", "alpha") { received += it.sourceAgentId }
        mesh.cancel("corr-x")
        val result = mesh.publish(event("src", "alpha", correlationId = "corr-x"))
        val delivery = delivered(result)
        assertEquals(emptyList<String>(), delivery.deliveredTo)
        awaitQueue(sunk, 1)
        assertEquals(listOf("alpha"), sunk.toList())
        assertTrue(mesh.isCancelled("corr-x"))
    }

    @Test
    fun queueOverflowReportedWhenBoundedCapacityExhausted() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope, defaultCapacity = 1)
        val received = ConcurrentLinkedQueue<String>()
        mesh.subscribe("a", "alpha", capacity = 1) { received += it.sourceAgentId; delay(200) }
        mesh.publish(event("src", "alpha", eventId = "o1", correlationId = "c1"))
        mesh.publish(event("src", "alpha", eventId = "o2", correlationId = "c2"))
        mesh.publish(event("src", "alpha", eventId = "o3", correlationId = "c3"))
        val fourth = mesh.publish(event("src", "alpha", eventId = "o4", correlationId = "c4"))
        assertTrue(fourth is MeshPublishResult.Delivered)
        assertTrue(delivered(fourth).queueOverflow.isNotEmpty())
    }

    @Test
    fun handlerThrowLeavesMeshAlive() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        var afterFailure: String? = null
        mesh.subscribe("boom", "alpha") { throw IllegalStateException("kaboom") }
        mesh.publish(event("src", "alpha"))
        mesh.subscribe("ok", "beta") { afterFailure = it.topic }
        mesh.publish(event("src", "beta"))
        val deadline = System.currentTimeMillis() + 2000
        while (afterFailure == null && System.currentTimeMillis() < deadline) delay(5)
        assertEquals("beta", afterFailure)
    }
}
