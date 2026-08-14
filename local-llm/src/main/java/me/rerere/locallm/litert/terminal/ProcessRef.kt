package me.rerere.locallm.litert.terminal

import kotlinx.serialization.Serializable
import me.rerere.locallm.litert.workspace.CommandEffectAnalyzer

/**
 * Phase F (roadmap F1). [ProcessRef] identifies a single OS process instance owned by a
 * [TerminalSession]. Agents never receive raw PIDs — only this opaque reference.
 *
 * [ProcessBackend] is the backend-neutral seam for actually running a command. It is the
 * single authority that maps a validated [ProcessCommand] to a real execution (plain mode
 * for deterministic noninteractive jobs, PTY where interactive genuinely requires it, SSH
 * via a separate adapter). A process is owned by exactly one runtime: [ProcessRef] is issued
 * only after the backend accepts the start.
 */
@Serializable
data class ProcessRef(val processId: String) {
    override fun toString(): String = "process:$processId"
}

/**
 * Everything required to start a process, pre-validated by the capability/effect gate
 * (roadmap F6) before [ProcessBackend.start] is ever called.
 */
@Serializable
data class ProcessCommand(
    val ref: ProcessRef,
    val command: List<String>,
    /** Resolved working-directory scope (broker-visible). */
    val workingDirectory: String? = null,
    /** Environment/credential references to inject, if the grant allows. */
    val env: Map<String, String> = emptyMap(),
    /** true only when interactive semantics genuinely require a PTY. */
    val requirePty: Boolean = false,
)

/**
 * The lifecycle status of a process, owned by [TerminalSession] (roadmap F2).
 */
enum class ProcessStatus {
    CREATED,
    STARTING,
    RUNNING,
    EXITED,
    FAILED,
    CANCELLED,
}

/**
 * Terminal signal about how a process ended (roadmap F8).
 */
enum class ProcessTermination {
    NORMAL,
    SIGNALED,
    TIMEOUT,
    CANCELLED,
    BACKEND_FAILURE,
}

/**
 * The raw result of running a process to completion.
 */
@Serializable
data class ProcessCompletion(
    val exitCode: Int,
    val termination: ProcessTermination,
    val startedAtMs: Long,
    val completedAtMs: Long,
)

/**
 * Backend-neutral process seam. [start] is only ever invoked after the broker gate
 * (CapabilityGrant + ResourceBudget + effect analysis) has authorized [ProcessCommand].
 */
fun interface ProcessBackend {
    /** Start [command] and return a handle that can produce output and await termination. */
    suspend fun start(command: ProcessCommand): RunningProcess
}

/**
 * A live process handle from [ProcessBackend.start]. Output is delivered as raw chunks on an
 * [ObservationStream]-style channel; this interface exposes the pull side.
 */
interface RunningProcess {
    val process: ProcessRef
    val status: ProcessStatus

    /** Request output as it is produced. Emits until the process exits or is cancelled. */
    fun output(): kotlinx.coroutines.channels.ReceiveChannel<TerminalChunk>

    /** Total output bytes produced so far (before any truncation cut). */
    val outputBytes: Long

    /** True when output exceeded the backend's cap and was truncated. */
    val outputTruncated: Boolean

    /** Await termination, returning the completion. */
    suspend fun awaitExit(): ProcessCompletion

    /** Write raw bytes to the process input (only if an input lease grants it). */
    suspend fun writeInput(bytes: ByteArray)

    /** Request cancellation (idempotent; RUNNING -> CANCELLED). */
    fun cancel()
}
