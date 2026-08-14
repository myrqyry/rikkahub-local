package me.rerere.locallm.litert.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ModelRuntimeManagerTest {

    private suspend fun CoroutineScope.concurrentMax(manager: ModelRuntimeManager, ref: String, policy: RuntimeConcurrency, n: Int, gate: CompletableDeferred<Unit> = CompletableDeferred()): Pair<List<Job>, AtomicInteger> {
        val peak = AtomicInteger(0)
        val entered = AtomicInteger(0)
        val jobs = (1..n).map {
            launch {
                val lease = manager.acquire(RuntimeRequest(ref, policy, maxParallelSessions = if (policy == RuntimeConcurrency.PARALLEL_SESSIONS) 2 else 1))
                entered.incrementAndGet()
                peak.accumulateAndGet(entered.get()) { a, b -> maxOf(a, b) }
                gate.await()
                entered.decrementAndGet()
                lease.close()
            }
        }
        return jobs to peak
    }

    @Test
    fun exclusiveSerializesSessions() = runBlocking {
        val manager = ModelRuntimeManager()
        val firstEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val peak = AtomicInteger(0)
        val active = AtomicInteger(0)
        val jobs = (1..2).map {
            launch {
                val lease = manager.acquire(RuntimeRequest("lm", RuntimeConcurrency.EXCLUSIVE))
                val n = active.incrementAndGet()
                peak.accumulateAndGet(n) { a, b -> maxOf(a, b) }
                firstEntered.complete(Unit)
                release.await()
                active.decrementAndGet()
                lease.close()
            }
        }
        firstEntered.await()
        assertEquals("only one EXCLUSIVE session may hold the runtime", 1, peak.get())
        assertEquals(1, manager.activeSessions("lm"))
        release.complete(Unit)
        jobs.forEach { it.join() }
        assertEquals("second session admitted after release", 1, peak.get())
        assertEquals(0, manager.activeSessions("lm"))
    }

    @Test
    fun serialSessionsQueueWithoutOverlap() = runBlocking {
        val manager = ModelRuntimeManager()
        val peak = AtomicInteger(0)
        val active = AtomicInteger(0)
        val releaseAll = CompletableDeferred<Unit>()
        val jobs = (1..3).map {
            launch {
                val lease = manager.acquire(RuntimeRequest("tts", RuntimeConcurrency.SERIAL_SESSIONS))
                val n = active.incrementAndGet()
                peak.accumulateAndGet(n) { a, b -> maxOf(a, b) }
                releaseAll.await()
                active.decrementAndGet()
                lease.close()
            }
        }
        releaseAll.complete(Unit)
        jobs.forEach { it.join() }
        assertEquals("serial sessions never overlap", 1, peak.get())
        assertEquals(0, manager.activeSessions("tts"))
    }

    @Test
    fun parallelAllowsUpToMaxParallelSessions() = runBlocking {
        val manager = ModelRuntimeManager()
        val twoEntered = CompletableDeferred<Unit>()
        val entered = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()
        val jobs = (1..3).map {
            launch {
                val lease = manager.acquire(RuntimeRequest("img", RuntimeConcurrency.PARALLEL_SESSIONS, maxParallelSessions = 2))
                val n = entered.incrementAndGet()
                peak.accumulateAndGet(n) { a, b -> maxOf(a, b) }
                if (entered.get() == 2) twoEntered.complete(Unit)
                release.await()
                entered.decrementAndGet()
                lease.close()
            }
        }
        twoEntered.await()
        assertEquals("third parallel session is blocked by the 2-permit ceiling", 2, peak.get())
        assertEquals(2, manager.activeSessions("img"))
        release.complete(Unit)
        jobs.forEach { it.join() }
        assertEquals("all sessions admitted once permits free", 2, peak.get())
        assertEquals(0, manager.activeSessions("img"))
    }

    @Test
    fun distinctRuntimesAreIndependent() = runBlocking {
        val manager = ModelRuntimeManager()
        val release = CompletableDeferred<Unit>()
        val jobs = listOf(
            launch {
                val lease = manager.acquire(RuntimeRequest("lm", RuntimeConcurrency.EXCLUSIVE))
                release.await()
                lease.close()
            },
            launch {
                val lease = manager.acquire(RuntimeRequest("tts", RuntimeConcurrency.EXCLUSIVE))
                release.await()
                lease.close()
            },
        )
        release.complete(Unit)
        jobs.forEach { it.join() }
        assertEquals(2, manager.registeredRuntimes().size)
        assertTrue(manager.registeredRuntimes().containsAll(listOf("lm", "tts")))
    }

    @Test
    fun closeReleasesAndIsIdempotent() = runBlocking {
        val manager = ModelRuntimeManager()
        val lease = manager.acquire(RuntimeRequest("lm", RuntimeConcurrency.EXCLUSIVE))
        assertEquals(1, manager.activeSessions("lm"))
        lease.close()
        lease.close()
        assertEquals("double-close releases only once", 0, manager.activeSessions("lm"))
        val lease2 = manager.acquire(RuntimeRequest("lm", RuntimeConcurrency.EXCLUSIVE))
        assertEquals("slot is reusable after close", 1, manager.activeSessions("lm"))
        lease2.close()
    }

    @Test
    fun leaseCarriesIdentityAndTimestamp() = runBlocking {
        val manager = ModelRuntimeManager(nowMs = { 1700000000000L })
        val lease = manager.acquire(
            RuntimeRequest(
                runtimeRef = "omni",
                concurrency = RuntimeConcurrency.SERIAL_SESSIONS,
                memoryAllocationBytes = 8L * 1024 * 1024 * 1024,
            )
        )
        assertEquals("omni", lease.runtimeRef)
        assertEquals(RuntimeConcurrency.SERIAL_SESSIONS, lease.concurrency)
        assertEquals(8L * 1024 * 1024 * 1024, lease.memoryAllocationBytes)
        assertEquals(1700000000000L, lease.acquiredAtMs)
        assertTrue(lease.leaseId.isNotBlank())
        assertTrue(lease.sessionId.isNotBlank())
        assertTrue(lease.leaseId != lease.sessionId)
        lease.close()
    }

    @Test
    fun firstRequestBindsPolicyPerRuntime() = runBlocking {
        val manager = ModelRuntimeManager()
        val gate = CompletableDeferred<Unit>()
        val peak = AtomicInteger(0)
        val active = AtomicInteger(0)
        // First acquirer asks for EXCLUSIVE on "lm".
        val j1 = launch {
            val lease = manager.acquire(RuntimeRequest("lm", RuntimeConcurrency.EXCLUSIVE))
            active.incrementAndGet()
            peak.accumulateAndGet(active.get()) { a, b -> maxOf(a, b) }
            gate.await()
            active.decrementAndGet()
            lease.close()
        }
        val j2 = launch {
            val lease = manager.acquire(RuntimeRequest("lm", RuntimeConcurrency.PARALLEL_SESSIONS, maxParallelSessions = 4))
            active.incrementAndGet()
            peak.accumulateAndGet(active.get()) { a, b -> maxOf(a, b) }
            gate.await()
            active.decrementAndGet()
            lease.close()
        }
        // j2 asks for 4 permits but the runtime's policy was already bound to EXCLUSIVE (1)
        // by j1; the later request must not raise the ceiling.
        gate.complete(Unit)
        j1.join(); j2.join()
        assertEquals("policy bound by first acquirer, later requests cannot raise it", 1, peak.get())
        assertEquals(0, manager.activeSessions("lm"))
    }
}
