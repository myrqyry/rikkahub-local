package me.rerere.locallm.litert.terminal

import kotlinx.serialization.Serializable

/**
 * Phase F (roadmap F4). A raw chunk of terminal output. Scrollback is a sequence of these.
 */
@Serializable
data class TerminalChunk(
    val process: ProcessRef,
    val stream: TerminalStream,
    val sequence: Long,
    val atMs: Long,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerminalChunk) return false
        return process == other.process &&
            stream == other.stream &&
            sequence == other.sequence &&
            atMs == other.atMs &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = process.hashCode()
        result = 31 * result + stream.hashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + atMs.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

enum class TerminalStream {
    STDOUT,
    STDERR,
    /** PTY-mixed output where stdout/stderr cannot be separated. */
    PTY,
}

/**
 * Phase F (roadmap F3). Who is allowed to write to a terminal's input at a given moment.
 */
enum class InputSource {
    USER,
    AGENT,
    PROCEDURE,
    SYSTEM,
}

/**
 * A grant to write to the process input stream. Issued by [TerminalSession] only to a single
 * owner at a time; ownership transfers are explicit.
 */
@Serializable
data class TerminalInputLease(
    val process: ProcessRef,
    val owner: InputSource,
    val leaseId: String,
    val expiresAtMs: Long? = null,
)

/**
 * A prompt-state descriptor surfaced in a [TerminalSnapshot] so agents can reason about
 * readiness (e.g. shell prompt, interactive app waiting on input) without parsing raw bytes.
 */
@Serializable
enum class PromptState {
    UNKNOWN,
    READY,
    WAITING_FOR_INPUT,
    RUNNING,
}

/**
 * Phase F (roadmap F4). A semantic snapshot of terminal state — not raw stdout. Agents act on
 * this, not on a byte stream.
 */
@Serializable
data class TerminalSnapshot(
    val process: ProcessRef,
    val revision: Long,
    val visibleText: String,
    val cwd: String? = null,
    val promptState: PromptState = PromptState.UNKNOWN,
    val exitCode: Int? = null,
)
