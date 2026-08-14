package me.rerere.locallm.litert.workspace

import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

sealed interface ShadowNode {
    data class Overlay(val content: ByteArray) : ShadowNode {
        override fun equals(other: Any?): Boolean = other is Overlay && content.contentEquals(other.content)
        override fun hashCode(): Int = content.contentHashCode()
    }

    data object Tombstone : ShadowNode
}

enum class DiffKind { ADDED, MODIFIED, DELETED }

@Serializable
data class DiffEntry(
    val path: String,
    val kind: DiffKind,
) {
    override fun toString(): String = "${kind.name.lowercase()}  $path"
}

data class WorkspaceDiff(
    val entries: List<DiffEntry>,
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

/**
 * Copy-on-write shadow workspace (roadmap E3).
 *
 * Reads pass through to the real backend unless overlaid; writes are captured in an
 * in-memory overlay only (never applied to the real workspace); deletes become
 * tombstones. [diff] produces a deterministic, sorted summary. [apply] materializes
 * the diff as concrete backend operations so a caller can request a real-write
 * capability and only then touch the environment.
 *
 * The shadow never mutates the underlying workspace.
 */
class ShadowWorkspace(private val backend: WorkspaceBackend) {

    private val overlay = LinkedHashMap<String, ShadowNode>()

    suspend fun read(path: String): ByteArray? {
        when (val node = overlay[path]) {
            is ShadowNode.Tombstone -> return null
            is ShadowNode.Overlay -> return node.content
            null -> {}
        }
        return when (val r = backend.execute(
            WorkspaceOperation(kind = WorkspaceOperationKind.READ, file = WorkspaceFileRef(WorkspaceRef("", ""), path)),
        )) {
            is WorkspaceResult.Read -> r.content
            else -> null
        }
    }

    suspend fun readText(path: String): String? =
        read(path)?.toString(StandardCharsets.UTF_8)

    suspend fun write(path: String, content: ByteArray) {
        overlay[path] = ShadowNode.Overlay(content)
    }

    suspend fun writeText(path: String, content: String) {
        write(path, content.toByteArray(StandardCharsets.UTF_8))
    }

    suspend fun delete(path: String) {
        overlay[path] = ShadowNode.Tombstone
    }

    suspend fun exists(path: String): Boolean = when (val node = overlay[path]) {
        is ShadowNode.Tombstone -> false
        is ShadowNode.Overlay -> true
        null -> read(path) != null
    }

    /** Deterministic diff: tombstones (deletes) first, then adds sorted by path. */
    fun diff(): WorkspaceDiff {
        fun rank(kind: DiffKind): Int = when (kind) {
            DiffKind.DELETED -> 0
            else -> 1
        }
        val entries = overlay.entries.map { (path, node) ->
            when (node) {
                is ShadowNode.Tombstone -> DiffEntry(path, DiffKind.DELETED)
                else -> DiffEntry(path, DiffKind.ADDED)
            }
        }.sortedWith(compareBy({ rank(it.kind) }, { it.path }))
        return WorkspaceDiff(entries)
    }

    /**
     * Materialize the diff as backend operations for an apply-after-approval flow.
     * Returns (operations, humanReadableSummary). No backend call happens here —
     * this only builds the operation list for the capability-gated real write.
     */
    fun apply(): Pair<List<WorkspaceOperation>, String> {
        val ops = mutableListOf<WorkspaceOperation>()
        for (entry in diff().entries) {
            when (entry.kind) {
                DiffKind.DELETED ->
                    ops += WorkspaceOperation(
                        kind = WorkspaceOperationKind.DELETE,
                        file = WorkspaceFileRef(WorkspaceRef("", ""), entry.path),
                    )

                else -> {
                    val content = (overlay[entry.path] as? ShadowNode.Overlay)?.content ?: byteArrayOf()
                    ops += WorkspaceOperation(
                        kind = WorkspaceOperationKind.WRITE,
                        file = WorkspaceFileRef(WorkspaceRef("", ""), entry.path),
                        content = content,
                    )
                }
            }
        }
        val sb = StringBuilder()
        for (entry in diff().entries) sb.append(entry).append('\n')
        return ops to sb.toString()
    }

    fun renderedDiff(): String {
        val sb = StringBuilder()
        for (entry in diff().entries) {
            when (entry.kind) {
                DiffKind.DELETED -> {
                    sb.append('-').append(' ').append(entry.path).append('\n')
                }

                else -> {
                    val content = (overlay[entry.path] as? ShadowNode.Overlay)?.content ?: ByteArrayOutputStream().toByteArray()
                    sb.append('+').append(' ').append(entry.path).append('\n')
                    sb.append(content.toString(StandardCharsets.UTF_8).lines().joinToString("\n") { "  " + it }).append('\n')
                }
            }
        }
        return sb.toString()
    }
}
