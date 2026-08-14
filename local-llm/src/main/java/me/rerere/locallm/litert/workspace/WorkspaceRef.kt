package me.rerere.locallm.litert.workspace

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceRef(
    val workspaceId: String,
    val root: String,
)

@Serializable
data class WorkspaceFileRef(
    val workspace: WorkspaceRef,
    val path: String,
)

@Serializable
enum class WorkspaceOperationKind {
    READ,
    WRITE,
    DELETE,
    LIST,
    RUN_PROCESS,
}

@Serializable
data class WorkspaceOperation(
    val kind: WorkspaceOperationKind,
    val file: WorkspaceFileRef? = null,
    val content: ByteArray? = null,
    val command: List<String> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WorkspaceOperation) return false
        if (kind != other.kind) return false
        if (file != other.file) return false
        if (!(content?.contentEquals(other.content ?: byteArrayOf()) ?: (other.content == null))) return false
        return command == other.command
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + (file?.hashCode() ?: 0)
        result = 31 * result + (content?.contentHashCode() ?: 0)
        result = 31 * result + command.hashCode()
        return result
    }
}

@Serializable
data class WorkspaceFileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val updatedAt: Long,
)

@Serializable
data class WorkspaceCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val truncated: Boolean = false,
)

sealed interface WorkspaceResult {
    data class Read(val content: ByteArray) : WorkspaceResult {
        override fun equals(other: Any?): Boolean =
            other is Read && content.contentEquals(other.content)

        override fun hashCode(): Int = content.contentHashCode()
    }

    data object Write : WorkspaceResult
    data object Delete : WorkspaceResult
    data class List(val entries: kotlin.collections.List<WorkspaceFileEntry>) : WorkspaceResult
    data class RunProcess(val result: WorkspaceCommandResult) : WorkspaceResult
    data class Failed(val error: String) : WorkspaceResult
}

fun interface WorkspaceBackend {
    suspend fun execute(op: WorkspaceOperation): WorkspaceResult
}
