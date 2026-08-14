package me.rerere.locallm.litert.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import me.rerere.locallm.litert.terminal.ObservationStream.SubscribeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationStreamTest {

    private fun stream(capacity: Int = 64): ObservationStream {
        // scope is reserved for future async fan-out; synchronous delivery makes tests deterministic.
        return ObservationStream(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), capacity)
    }

    @Test
    fun `subscribe and emit delivers to the subscribed agent`() = runBlocking {
        val s = stream()
        val received = mutableListOf<ObservationEvent>()
        assertEquals(SubscribeResult.Subscribed, s.subscribe("agent-1") { received += it })
        val ev = ObservationEvent.ProcessStarted(ProcessRef("p1"), listOf("echo", "hi"), 100L)
        s.emit(ev)
        assertEquals(listOf(ev), received)
    }

    @Test
    fun `duplicate subscribe is rejected`() = runBlocking {
        val s = stream()
        s.subscribe("agent-1") {}
        val second = s.subscribe("agent-1") {}
        assertTrue(second is SubscribeResult.Rejected)
    }

    @Test
    fun `bounded capacity overflow notifies via RuntimeError`() = runBlocking {
        val s = stream(capacity = 1)
        val received = mutableListOf<ObservationEvent>()
        s.subscribe("agent-1") { received += it }
        s.emit(ObservationEvent.ProcessStarted(ProcessRef("p1"), listOf("a"), 1L))
        s.emit(ObservationEvent.ProcessStarted(ProcessRef("p2"), listOf("b"), 2L))
        assertEquals(2, received.size)
        assertTrue(received[0] is ObservationEvent.ProcessStarted)
        assertTrue(received[1] is ObservationEvent.RuntimeError)
        assertTrue(s.overflowCount >= 1)
    }

    @Test
    fun `a throwing subscriber does not block others`() = runBlocking {
        val s = stream()
        val good = mutableListOf<ObservationEvent>()
        s.subscribe("bad") { throw IllegalStateException("boom") }
        s.subscribe("good") { good += it }
        val ev = ObservationEvent.ProcessStarted(ProcessRef("p1"), listOf("x"), 5L)
        s.emit(ev)
        assertEquals(listOf(ev), good)
    }

    @Test
    fun `unsubscribe stops delivery`() = runBlocking {
        val s = stream()
        val received = mutableListOf<ObservationEvent>()
        s.subscribe("agent-1") { received += it }
        s.unsubscribe("agent-1")
        s.emit(ObservationEvent.ProcessStarted(ProcessRef("p1"), listOf("x"), 1L))
        assertTrue(received.isEmpty())
        assertEquals(0, s.subscriberCount)
    }

    @Test
    fun `process output equality and hash are by bytes content`() {
        val p = ProcessRef("p1")
        val a = ObservationEvent.ProcessOutput(p, TerminalStream.STDOUT, "hi".toByteArray(), 1L)
        val b = ObservationEvent.ProcessOutput(p, TerminalStream.STDOUT, "hi".toByteArray(), 1L)
        val c = ObservationEvent.ProcessOutput(p, TerminalStream.STDOUT, "he".toByteArray(), 1L)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}
