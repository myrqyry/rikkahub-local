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
 * Copy-on-write shadow workspace (roadmap E3 / F0).
 *
 * Reads pass through to the real backend unless overlaid; writes are captured in an
 * in-memory overlay only (never applied to the real workspace); deletes become
 * tombstones. [diff] produces a deterministic, sorted summary that distinguishes an
 * [DiffKind.ADDED] overlay (path did not exist in the base) from a [DiffKind.MODIFIED]
 * overlay (path existed in the base). [apply] materializes the diff as concrete backend
 * operations so a caller can request a real-write capability and only then touch the
 * environment.
 *
 * The shadow never mutates the underlying workspace. Every generated
 * [WorkspaceFileRef] carries the real [WorkspaceRef] this shadow belongs to — blank or
 * mismatched refs are impossible by construction.
 */
class ShadowWorkspace(
    private val workspace: WorkspaceRef,
    private val backend: WorkspaceBackend,
) {

    private val overlay = LinkedHashMap<String, ShadowNode>()

    /** Presence of a path in the base workspace, captured at first mutation. */
    private val basePresence = HashMap<String, Boolean>()

    private fun fileRef(path: String): WorkspaceFileRef = WorkspaceFileRef(workspace, path)

    /** Raw backend read that bypasses the overlay (used for base-presence tracking). */
    private suspend fun baseRead(path: String): ByteArray? = when (val r = backend.execute(
        WorkspaceOperation(kind = WorkspaceOperationKind.READ, file = fileRef(path)),
    )) {
        is WorkspaceResult.Read -> r.content
        else -> null
    }

    suspend fun read(path: String): ByteArray? {
        when (val node = overlay[path]) {
            is ShadowNode.Tombstone -> return null
            is ShadowNode.Overlay -> return node.content
            null -> {}
        }
        return baseRead(path)
    }

    suspend fun readText(path: String): String? =
        read(path)?.toString(StandardCharsets.UTF_8)

    suspend fun write(path: String, content: ByteArray) {
        if (path !in overlay) {
            basePresence[path] = baseRead(path) != null
        }
        overlay[path] = ShadowNode.Overlay(content)
    }

    suspend fun writeText(path: String, content: String) {
        write(path, content.toByteArray(StandardCharsets.UTF_8))
    }

    suspend fun delete(path: String) {
        if (path !in overlay) {
            basePresence[path] = baseRead(path) != null
        }
        overlay[path] = ShadowNode.Tombstone
    }

    suspend fun exists(path: String): Boolean = when (val node = overlay[path]) {
        is ShadowNode.Tombstone -> false
        is ShadowNode.Overlay -> true
        null -> read(path) != null
    }

    /** Deterministic diff: tombstones (deletes) first, then adds/modifies sorted by path. */
    fun diff(): WorkspaceDiff {
        fun rank(kind: DiffKind): Int = when (kind) {
            DiffKind.DELETED -> 0
            else -> 1
        }
        val entries = overlay.entries.map { (path, node) ->
            when (node) {
                is ShadowNode.Tombstone -> DiffEntry(path, DiffKind.DELETED)
                is ShadowNode.Overlay ->
                    // A path that existed in the base workspace is an edit, not a new file.
                    DiffEntry(path, if (basePresence[path] == true) DiffKind.MODIFIED else DiffKind.ADDED)
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
                        file = fileRef(entry.path),
                    )

                else -> {
                    val content = (overlay[entry.path] as? ShadowNode.Overlay)?.content ?: byteArrayOf()
                    ops += WorkspaceOperation(
                        kind = WorkspaceOperationKind.WRITE,
                        file = fileRef(entry.path),
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
