package me.rerere.rikkahub.data.agentrun

import kotlinx.serialization.json.Json
import me.rerere.agentruntime.ContinuationCheckpoint
import me.rerere.agentruntime.ContinuationCheckpointDraft
import me.rerere.agentruntime.ContinuationSnapshot
import me.rerere.agentruntime.ContinuationStore
import me.rerere.rikkahub.data.db.entity.ContinuationCheckpointDao
import me.rerere.rikkahub.data.db.entity.ContinuationCheckpointEntity

class RoomContinuationStore(
    private val dao: ContinuationCheckpointDao,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ContinuationStore {
    override suspend fun append(draft: ContinuationCheckpointDraft): ContinuationCheckpoint =
        dao.append(draft, System.currentTimeMillis(), json.encodeToString(ContinuationSnapshot.serializer(), draft.snapshot))
            .toCheckpoint()

    override suspend fun latest(runId: String): ContinuationCheckpoint? =
        dao.latest(runId)?.toCheckpoint()

    override suspend fun list(runId: String): List<ContinuationCheckpoint> =
        dao.list(runId).map { it.toCheckpoint() }

    private fun ContinuationCheckpointEntity.toCheckpoint() = ContinuationCheckpoint(
        id = id,
        runId = runId,
        sequence = sequence,
        createdAtMs = createdAtMs,
        verifiedAtMs = verifiedAtMs,
        snapshot = json.decodeFromString(ContinuationSnapshot.serializer(), snapshotJson),
    )
}
