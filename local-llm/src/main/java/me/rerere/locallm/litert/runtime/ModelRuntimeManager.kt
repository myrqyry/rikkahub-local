package me.rerere.locallm.litert.runtime

import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * How a runtime lets consumers use its sessions. A runtime declares ONE policy; the
 * manager enforces it for every consumer that acquires a lease on that runtime.
 *
 * LiteRT-LM, Omni TTS, image and experimental runtimes have genuinely different
 * native lifetime behaviour. The manager does NOT pretend they share thread-safety;
 * it only enforces the coarse concurrency ceiling the runtime itself can tolerate.
 */
enum class RuntimeConcurrency {
    /** One session on the runtime at any time. A second acquire suspends until the
     *  first lease is closed. Suits runtimes with a single native engine handle. */
    EXCLUSIVE,

    /** Sessions run one after another (queued, never overlapping). Like [EXCLUSIVE]
     *  in concurrency, but marks the runtime as *serialised* by design — e.g. a TTS
     *  engine that must reuse one session for waveform continuity. */
    SERIAL_SESSIONS,

    /** Up to [RuntimeRequest.maxParallelSessions] sessions may be active at once.
     *  Suits runtimes that are internally thread-safe (or that isolate state per
     *  session) and can share the native allocator without corruption. */
    PARALLEL_SESSIONS,
}

/**
 * A request to run on a named runtime under a given concurrency ceiling.
 *
 * [concurrency] and [maxParallelSessions] are bound the FIRST time this runtime is
 * acquired and apply to all subsequent consumers of the same [runtimeRef] — the
 * runtime's policy is fixed by its first acquirer and later requests must be
 * compatible. This keeps the manager deterministic.
 */
data class RuntimeRequest(
    val runtimeRef: String,
    val concurrency: RuntimeConcurrency = RuntimeConcurrency.EXCLUSIVE,
    val maxParallelSessions: Int = 1,
    /** Optional upper bound on how much accelerator/RAM the session may claim. */
    val memoryAllocationBytes: Long? = null,
)

/**
 * A temporary right to run one session on a runtime. Call [close] when the session is
 * done; doing so returns the slot to the runtime's pool. [close] is idempotent.
 *
 * Leases, not global singleton getters: the [ModelRuntimeManager] is the only way to
 * touch a runtime, so two consumers can never double-load an engine, stack duplicate
 * native allocations, overlap KV caches, or race teardown.
 */
class RuntimeLease(
    val leaseId: String,
    val runtimeRef: String,
    val sessionId: String,
    val concurrency: RuntimeConcurrency,
    val memoryAllocationBytes: Long?,
    val acquiredAtMs: Long,
    private val onClose: () -> Unit,
) {
    @Volatile
    private var closed = false

    /** Release the slot. Safe to call more than once (only the first releases). */
    fun close() {
        if (!closed) {
            closed = true
            onClose()
        }
    }
}

/**
 * Hands out [RuntimeLease]s for named runtimes, enforcing each runtime's concurrency
 * ceiling via a suspending [Semaphore]. Pure JVM / deterministic; no engine or native
 * code lives here — a concrete runtime backend (LiteRT-LM, Omni TTS, …) is wired
 * app-side through the lease it receives.
 *
 * Per-runtime state:
 *  - a [Semaphore] with `permits` from the runtime's first [RuntimeRequest]
 *    (1 for [RuntimeConcurrency.EXCLUSIVE]/[RuntimeConcurrency.SERIAL_SESSIONS],
 *    [RuntimeRequest.maxParallelSessions] for [RuntimeConcurrency.PARALLEL_SESSIONS]);
 *  - an [AtomicInteger] active-session counter for [activeSessions].
 *
 * `acquire` suspends until a slot is free; `close` on the returned lease releases it.
 * Because a [Semaphore] has no owner, a lease acquired in one coroutine can be closed
 * in another — exactly the hand-off a session owns-then-releases needs.
 */
class ModelRuntimeManager(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val semaphores = ConcurrentHashMap<String, Semaphore>()
    private val active = ConcurrentHashMap<String, AtomicInteger>()
    private val sessionSeq = AtomicLong(0)

    /** Block until a slot on [request.runtimeRef] is free, then return a lease. */
    suspend fun acquire(request: RuntimeRequest): RuntimeLease {
        val semaphore = semaphores.computeIfAbsent(request.runtimeRef) {
            val permits = when (request.concurrency) {
                RuntimeConcurrency.EXCLUSIVE,
                RuntimeConcurrency.SERIAL_SESSIONS -> 1
                RuntimeConcurrency.PARALLEL_SESSIONS -> request.maxParallelSessions.coerceAtLeast(1)
            }
            Semaphore(permits)
        }
        semaphore.acquire()
        active.computeIfAbsent(request.runtimeRef) { AtomicInteger(0) }.incrementAndGet()
        return RuntimeLease(
            leaseId = "lease-" + sessionSeq.incrementAndGet(),
            runtimeRef = request.runtimeRef,
            sessionId = "session-" + sessionSeq.incrementAndGet(),
            concurrency = request.concurrency,
            memoryAllocationBytes = request.memoryAllocationBytes,
            acquiredAtMs = nowMs(),
        ) { releaseSlot(request.runtimeRef, semaphore) }
    }

    private fun releaseSlot(runtimeRef: String, semaphore: Semaphore) {
        active[runtimeRef]?.decrementAndGet()
        semaphore.release()
    }

    /** Number of currently-held (not yet closed) sessions on [runtimeRef]. */
    fun activeSessions(runtimeRef: String): Int = active[runtimeRef]?.get() ?: 0

    /** Runtime refs that have been acquired at least once this session. */
    fun registeredRuntimes(): Set<String> = semaphores.keys
}
