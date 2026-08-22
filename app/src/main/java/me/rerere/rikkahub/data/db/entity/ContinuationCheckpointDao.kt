package me.rerere.rikkahub.data.db.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import me.rerere.agentruntime.ContinuationCheckpointDraft

@Dao
interface ContinuationCheckpointDao {
    @Query("SELECT * FROM continuation_checkpoints WHERE id = :id")
    suspend fun getById(id: String): ContinuationCheckpointEntity?

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM continuation_checkpoints WHERE run_id = :runId")
    suspend fun nextSequence(runId: String): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ContinuationCheckpointEntity): Long

    @Query("SELECT * FROM continuation_checkpoints WHERE run_id = :runId ORDER BY sequence DESC LIMIT 1")
    suspend fun latest(runId: String): ContinuationCheckpointEntity?

    @Query("SELECT * FROM continuation_checkpoints WHERE run_id = :runId ORDER BY sequence ASC")
    suspend fun list(runId: String): List<ContinuationCheckpointEntity>

    @Transaction
    suspend fun append(
        draft: ContinuationCheckpointDraft,
        createdAtMs: Long,
        snapshotJson: String,
    ): ContinuationCheckpointEntity {
        getById(draft.id)?.let { return it }
        require(draft.verifiedAtMs > 0) { "verifiedAtMs must be positive" }
        val entity = ContinuationCheckpointEntity(
            id = draft.id,
            runId = draft.runId,
            sequence = nextSequence(draft.runId),
            createdAtMs = createdAtMs,
            verifiedAtMs = draft.verifiedAtMs,
            snapshotJson = snapshotJson,
        )
        if (insert(entity) != -1L) return entity
        return checkNotNull(getById(draft.id))
    }
}
