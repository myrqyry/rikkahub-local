package me.rerere.agentruntime

import kotlinx.serialization.Serializable

/** Compact state needed to manually continue from a verified boundary. */
@Serializable
data class ContinuationSnapshot(
    val goal: String,
    val pendingWork: String,
    val lastVerifiedAction: String,
    val blocker: String? = null,
    val touchedResources: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val verificationState: String,
    val evidenceIds: List<String> = emptyList(),
)

/** Caller-owned checkpoint identity and verified continuation payload. */
data class ContinuationCheckpointDraft(
    val id: String,
    val runId: String,
    val verifiedAtMs: Long,
    val snapshot: ContinuationSnapshot,
)

/** Immutable checkpoint after a store assigns its stable per-run sequence. */
data class ContinuationCheckpoint(
    val id: String,
    val runId: String,
    val sequence: Long,
    val createdAtMs: Long,
    val verifiedAtMs: Long,
    val snapshot: ContinuationSnapshot,
)

interface ContinuationStore {
    suspend fun append(draft: ContinuationCheckpointDraft): ContinuationCheckpoint

    suspend fun latest(runId: String): ContinuationCheckpoint?

    suspend fun list(runId: String): List<ContinuationCheckpoint>
}

class InMemoryContinuationStore(
    private val now: () -> Long = System::currentTimeMillis,
) : ContinuationStore {
    private val records = LinkedHashMap<String, ContinuationCheckpoint>()

    override suspend fun append(draft: ContinuationCheckpointDraft): ContinuationCheckpoint {
        records[draft.id]?.let { return it }
        require(draft.verifiedAtMs > 0) { "verifiedAtMs must be positive" }

        val stored = ContinuationCheckpoint(
            id = draft.id,
            runId = draft.runId,
            sequence = records.values.count { it.runId == draft.runId } + 1L,
            createdAtMs = now(),
            verifiedAtMs = draft.verifiedAtMs,
            snapshot = draft.snapshot,
        )
        records[stored.id] = stored
        return stored
    }

    override suspend fun latest(runId: String): ContinuationCheckpoint? =
        records.values.lastOrNull { it.runId == runId }

    override suspend fun list(runId: String): List<ContinuationCheckpoint> =
        records.values.filter { it.runId == runId }
}
