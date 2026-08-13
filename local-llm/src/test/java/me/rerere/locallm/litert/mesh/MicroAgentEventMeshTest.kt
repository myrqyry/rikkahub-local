package me.rerere.locallm.litert.mesh

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicroAgentEventMeshTest {

    private fun event(source: String, topic: String, atMs: Long = 0L) =
        MicroAgentEvent(source, topic, buildJsonObject { put("k", source) }, atMs)

    @Test
    fun deliversOnlyToTopicMatchedSubscribers() = runBlocking {
        val mesh = MicroAgentEventMesh()
        val received = mutableListOf<String>()
        mesh.subscribe("a", "alpha") { received += "a:${it.topic}" }
        mesh.subscribe("b", "beta") { received += "b:${it.topic}" }

        val delivery = mesh.publish(event("src", "alpha"))

        assertEquals(listOf("a"), delivery.deliveredTo)
        assertEquals(listOf("a:alpha"), received)
    }

    @Test
    fun wildcardSubscriberReceivesAllTopics() = runBlocking {
        val mesh = MicroAgentEventMesh()
        val received = mutableListOf<String>()
        mesh.subscribe("all") { received += "${it.topic}" }

        mesh.publish(event("src", "alpha"))
        mesh.publish(event("src", "beta"))

        assertEquals(listOf("alpha", "beta"), received)
    }

    @Test
    fun deterministicOrderByAgentId() = runBlocking {
        val mesh = MicroAgentEventMesh()
        val order = mutableListOf<String>()
        mesh.subscribe("z") { order += "z" }
        mesh.subscribe("m") { order += "m" }
        mesh.subscribe("a") { order += "a" }

        mesh.publish(event("src", "t"))

        assertEquals(listOf("a", "m", "z"), order)
    }

    @Test
    fun failingSubscriberDoesNotBlockOthers() = runBlocking {
        val mesh = MicroAgentEventMesh()
        val received = mutableListOf<String>()
        mesh.subscribe("a") { error("boom") }
        mesh.subscribe("b") { received += "b" }

        val delivery = mesh.publish(event("src", "t"))

        assertEquals(listOf("b"), delivery.deliveredTo)
        assertEquals(listOf("b"), received)
    }

    @Test
    fun noSubscribersStillStampsAndForwardsToSink() = runBlocking {
        val sunk = mutableListOf<MicroAgentEvent>()
        val mesh = MicroAgentEventMesh(sink = MicroAgentEventSink { sunk += it }, nowMs = { 1234L })

        val delivery = mesh.publish(event("src", "t"))

        assertEquals(emptyList<String>(), delivery.deliveredTo)
        assertEquals(1234L, delivery.event.atMs)
        assertEquals(1, sunk.size)
        assertEquals(1234L, sunk.single().atMs)
    }

    @Test
    fun sinkReceivesEveryPublishedEvent() = runBlocking {
        val sunk = mutableListOf<MicroAgentEvent>()
        val mesh = MicroAgentEventMesh(sink = MicroAgentEventSink { sunk += it })
        mesh.subscribe("a") {}

        mesh.publish(event("src", "t1"))
        mesh.publish(event("src", "t2"))

        assertEquals(listOf("t1", "t2"), sunk.map { it.topic })
    }

    @Test
    fun topicMatchingIsCaseSensitive() = runBlocking {
        val mesh = MicroAgentEventMesh()
        val received = mutableListOf<String>()
        mesh.subscribe("a", "Alpha") { received += "got" }

        mesh.publish(event("src", "alpha"))

        assertEquals(emptyList<String>(), received)
    }

    @Test
    fun preservesCallerSuppliedTimestamp() = runBlocking {
        val mesh = MicroAgentEventMesh(nowMs = { 1L })

        val delivery = mesh.publish(event("src", "t", atMs = 999L))

        assertEquals(999L, delivery.event.atMs)
    }

    @Test
    fun resubscribeReplacesExistingSubscription() = runBlocking {
        val mesh = MicroAgentEventMesh()
        val received = mutableListOf<String>()
        mesh.subscribe("a", "alpha") { received += "first" }
        mesh.subscribe("a", "beta") { received += "second" }

        mesh.publish(event("src", "beta"))

        assertEquals(listOf("second"), received)
        assertEquals(1, mesh.subscriberCount())
    }

    @Test
    fun unsubscribeStopsDelivery() = runBlocking {
        val mesh = MicroAgentEventMesh()
        val received = mutableListOf<String>()
        mesh.subscribe("a", "alpha") { received += "got" }

        mesh.publish(event("src", "alpha"))
        mesh.unsubscribe("a")
        mesh.publish(event("src", "alpha"))

        assertEquals(listOf("got"), received)
    }
}
