package me.rerere.locallm.litert.terminal

import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase F (roadmap F7). Thin seam over the OS process primitives. [RealProcessUnderlay] wraps
 * [ProcessBuilder]; tests inject a deterministic underlay so no real binaries run in unit tests.
 *
 * The underlay deliberately exposes only what a process backend needs and is kept synchronous
 * (blocking) so it stays a trivial adapter over [java.lang.Process].
 */
interface ProcessUnderlay {
    fun start(command: List<String>, env: Map<String, String>, dir: String?): UnderlayProcess

    interface UnderlayProcess {
        val pid: Long

        /** Write bytes to the process stdin (does not close the stream). */
        fun writeStdin(bytes: ByteArray)

        /** Close the stdin stream, signalling EOF to the process. */
        fun closeStdin()

        val stdout: InputStream
        val stderr: InputStream

        /** Best-effort destroy (SIGTERM on POSIX). */
        fun destroy()

        /**
         * Block until the process exits and return its exit code. Maps to
         * [java.lang.Process.waitFor]. Implementations must be safe to call after [destroy].
         */
        fun awaitExit(): Int
    }
}

/** [ProcessUnderlay] backed by [java.lang.ProcessBuilder]. */
class RealProcessUnderlay : ProcessUnderlay {
    override fun start(command: List<String>, env: Map<String, String>, dir: String?): ProcessUnderlay.UnderlayProcess {
        val builder = ProcessBuilder(command)
        if (dir != null) builder.directory(File(dir))
        builder.environment().putAll(env)
        val proc = builder.start()
        return object : ProcessUnderlay.UnderlayProcess {
            // ponytail: java.lang.Process.pid() is Java 9+, unresolved against the Android minSdk 26
        // Test doubles supply the process details; the pid is only cosmetic for now.
            override val pid: Long get() = 0L
            override fun writeStdin(bytes: ByteArray) {
                proc.outputStream.write(bytes)
                proc.outputStream.flush()
            }

            override fun closeStdin() = proc.outputStream.close()
            override val stdout: InputStream get() = proc.inputStream
            override val stderr: InputStream get() = proc.errorStream
            override fun destroy() = proc.destroy()
            override fun awaitExit(): Int {
                proc.waitFor()
                return proc.exitValue()
            }
        }
    }
}

/**
 * Phase F (roadmap F7). Plain-mode process backend: spawns a real subprocess via [ProcessUnderlay]
 * and pumps its stdout/stderr into a [TerminalChunk] channel with a shared, monotonically
 * increasing sequence. Used for deterministic noninteractive jobs (`git status`, `pnpm test`,
 * `./gradlew test`). PTY and SSH adapters are separate concerns and are NOT implemented here.
 *
 * [maxOutputBytes] bounds the total output pumped per process (default 1MB); once exceeded the
 * backend stops pumping and marks the run truncated ([LocalRunningProcess.outputTruncated]).
 */
class LocalProcessBackend(
    private val underlay: ProcessUnderlay = RealProcessUnderlay(),
    private val maxOutputBytes: Long = 1024L * 1024L,
) : ProcessBackend {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun start(command: ProcessCommand): RunningProcess {
        val underlayProcess = underlay.start(command.command, command.env, command.workingDirectory)
        return LocalRunningProcess(scope, command.ref, underlayProcess, maxOutputBytes)
    }
}

/** [RunningProcess] backed by a live [ProcessUnderlay.UnderlayProcess]. */
private class LocalRunningProcess(
    private val scope: CoroutineScope,
    override val process: ProcessRef,
    private val underlay: ProcessUnderlay.UnderlayProcess,
    private val maxOutputBytes: Long,
) : RunningProcess {

    private val startedAtMs = System.currentTimeMillis()
    private val statusRef = AtomicReference(ProcessStatus.CREATED)

    private val channel = Channel<TerminalChunk>(Channel.UNLIMITED)
    private val sequence = AtomicLong(0)
    private val totalBytes = AtomicLong(0)
    private val truncated = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val pumpJobs = mutableListOf<Job>()

    init {
        statusRef.set(ProcessStatus.STARTING)
        pump(underlay.stdout, TerminalStream.STDOUT)
        pump(underlay.stderr, TerminalStream.STDERR)
        statusRef.set(ProcessStatus.RUNNING)
        // Close the output channel once both pumps reach EOF (i.e. the process closed its pipes).
        scope.launch {
            pumpJobs.forEach { it.join() }
            channel.close()
        }
    }

    private fun pump(stream: InputStream, streamType: TerminalStream) {
        val job = scope.launch {
            try {
                val buf = ByteArray(8192)
                while (true) {
                    if (cancelled.get() || truncated.get()) break
                    val n = withContext(Dispatchers.IO) { stream.read(buf) }
                    if (n < 0) break
                    val before = totalBytes.get()
                    if (before + n > maxOutputBytes) {
                        truncated.set(true)
                        val keep = (maxOutputBytes - before).coerceAtLeast(0).toInt()
                        if (keep > 0) {
                            totalBytes.set(before + keep)
                            channel.trySend(
                                TerminalChunk(process, streamType, sequence.getAndIncrement(), System.currentTimeMillis(), buf.copyOfRange(0, keep)),
                            )
                        }
                        break
                    }
                    totalBytes.set(before + n)
                    channel.trySend(
                        TerminalChunk(process, streamType, sequence.getAndIncrement(), System.currentTimeMillis(), buf.copyOfRange(0, n)),
                    )
                }
            } catch (_: Exception) {
                // Stream closed by cancel/destroy; nothing further to pump.
            }
        }
        pumpJobs.add(job)
    }

    override val status: ProcessStatus get() = statusRef.get()

    override fun output(): ReceiveChannel<TerminalChunk> = channel

    /** Total output bytes pumped (before any truncation cut). */
    override val outputBytes: Long get() = totalBytes.get()

    /** True when output exceeded [maxOutputBytes] and was truncated. */
    override val outputTruncated: Boolean get() = truncated.get()

    override suspend fun awaitExit(): ProcessCompletion {
        if (cancelled.get()) {
            return ProcessCompletion(-1, ProcessTermination.CANCELLED, startedAtMs, System.currentTimeMillis())
        }
        pumpJobs.forEach { it.join() }
        val exitCode = withContext(Dispatchers.IO) { underlay.awaitExit() }
        statusRef.set(ProcessStatus.EXITED)
        return ProcessCompletion(exitCode, ProcessTermination.NORMAL, startedAtMs, System.currentTimeMillis())
    }

    override suspend fun writeInput(bytes: ByteArray) {
        withContext(Dispatchers.IO) { underlay.writeStdin(bytes) }
    }

    override fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            statusRef.set(ProcessStatus.CANCELLED)
            underlay.destroy()
            pumpJobs.forEach { it.cancel() }
            channel.close()
        }
    }
}
