package me.rerere.locallm.litert.terminal

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Phase F (roadmap F2). [TerminalSession] is the single lifecycle owner of a process and its
 * input. It is NOT a shell, filesystem, SSH, permissions, logs, process-manager, or workflow
 * engine — it owns process interaction only.
 *
 * A session moves through [SessionStatus] and owns exactly one live [ProcessRef]. The session
 * issues [TerminalInputLease]s (F3) to a single [InputSource] owner at a time and maintains a
 * bounded [TerminalChunk] scrollback (F4). Closing a session is idempotent: the first close
 * wins and transitions the session to a terminal status.
 */
class TerminalSession private constructor(
    private val sessionId: String,
    private val process: ProcessRef,
    private val backend: ProcessBackend,
    private val maxScrollback: Int = 1024,
) {
    private val statusLock = Any()
    private var status: SessionStatus = SessionStatus.CREATED

    private val scrollback = ArrayDeque<TerminalChunk>()
    private val chunksSeen = java.util.concurrent.atomic.AtomicLong(0)

    private val running = ConcurrentHashMap.newKeySet<ProcessRef>()
    private var runningHandle: RunningProcess? = null

    private var closed = false

    companion object {
        /**
         * Create a session and issue the initial [ProcessRef]. The ref is reserved but the
         * process is not started until [start] is called (gate is between create and start).
         */
        fun create(sessionId: String, backend: ProcessBackend, maxScrollback: Int = 1024): TerminalSession {
            return TerminalSession(
                sessionId = sessionId,
                process = ProcessRef("$sessionId"),
                backend = backend,
                maxScrollback = maxScrollback,
            )
        }
    }

    val sessionRef: String get() = sessionId
    val processRef: ProcessRef get() = process
    val currentStatus: SessionStatus get() = synchronized(statusLock) { status }

    /**
     * Start the process via the backend. Returns a handle whose output channel the caller may
     * observe. Refuses to start if already started or closed. Idempotent on repeated start of a
     * terminal (non-terminal) status: returns the existing handle.
     */
    suspend fun start(command: ProcessCommand): RunningProcess {
        synchronized(statusLock) {
            check(!closed) { "session $sessionId is closed" }
            if (status != SessionStatus.CREATED) {
                @Suppress("UNCHECKED_CAST")
                return runningHandle as RunningProcess
            }
            status = SessionStatus.STARTING
        }
        val handle = backend.start(command)
        running.add(process)
        runningHandle = handle
        synchronized(statusLock) { status = SessionStatus.RUNNING }
        return handle
    }

    /**
     * Record a raw output chunk into scrollback (bounded). Returns true if this chunk was the
     * first for its sequence (dedupe for safety) — practically always true.
     */
    fun recordChunk(chunk: TerminalChunk): Boolean {
        if (chunk.sequence != chunksSeen.get()) return false
        chunksSeen.incrementAndGet()
        synchronized(scrollback) {
            scrollback.addLast(chunk)
            while (scrollback.size > maxScrollback) scrollback.removeFirst()
        }
        return true
    }

    /** Snapshot of raw chunks in scrollback, oldest first. */
    fun scrollbackSnapshot(): List<TerminalChunk> = synchronized(scrollback) { scrollback.toList() }

    /**
     * Build a semantic [TerminalSnapshot] from the raw scrollback (F4). This is what agents act
     * on — not the byte stream. Appends visible text of every chunk; callers may refine cwd /
     * prompt / exitCode from observation.
     */
    fun snapshot(revision: Long, cwd: String? = null, prompt: PromptState = PromptState.UNKNOWN): TerminalSnapshot {
        val text = scrollbackSnapshot().joinToString("") { it.bytes.toString(Charsets.UTF_8) }
        return TerminalSnapshot(
            process = process,
            revision = revision,
            visibleText = text,
            cwd = cwd,
            promptState = prompt,
            exitCode = if (currentStatus == SessionStatus.EXITED) 0 else null,
        )
    }

    /**
     * Idempotent close. First close wins; later calls no-op. Transitions to CANCELLED if the
     * process was still running (an active handle may still be awaited by the caller).
     */
    fun close() {
        synchronized(statusLock) {
            if (closed) return
            closed = true
            if (status == SessionStatus.RUNNING || status == SessionStatus.STARTING) {
                status = SessionStatus.CANCELLED
                runningHandle?.cancel()
            }
        }
        running.remove(process)
    }

    /** Transition to a terminal status (EXITED / FAILED) from a running state, idempotently. */
    fun markTerminal(next: SessionStatus) {
        require(next == SessionStatus.EXITED || next == SessionStatus.FAILED) { "markTerminal only accepts EXITED/FAILED" }
        synchronized(statusLock) {
            if (closed || status == SessionStatus.EXITED || status == SessionStatus.FAILED || status == SessionStatus.CANCELLED) return
            status = next
        }
        running.remove(process)
    }
}

enum class SessionStatus {
    CREATED,
    STARTING,
    RUNNING,
    EXITED,
    FAILED,
    CANCELLED,
}
