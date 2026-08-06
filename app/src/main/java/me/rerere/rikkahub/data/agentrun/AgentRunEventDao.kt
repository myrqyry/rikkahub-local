package me.rerere.rikkahub.data.agentrun

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentRunEventDao {
    @Insert
    suspend fun insert(event: AgentRunEvent)

    @Query("SELECT COALESCE(MAX(sequence), -1) + 1 FROM agent_run_events WHERE run_id = :runId")
    suspend fun findNextSequence(runId: String): Long

    @Query("SELECT * FROM agent_run_events WHERE run_id = :runId ORDER BY sequence ASC")
    fun findByRun(runId: String): Flow<List<AgentRunEvent>>

    @Query("DELETE FROM agent_run_events WHERE run_id = :runId")
    suspend fun deleteByRun(runId: String)
}
