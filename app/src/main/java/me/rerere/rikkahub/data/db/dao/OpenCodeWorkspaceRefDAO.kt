package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.OpenCodeWorkspaceRefEntity

@Dao
interface OpenCodeWorkspaceRefDAO {
    @Query("SELECT * FROM opencode_workspace_refs ORDER BY updated_at DESC")
    fun listFlow(): Flow<List<OpenCodeWorkspaceRefEntity>>

    @Query("SELECT * FROM opencode_workspace_refs WHERE id = :id")
    suspend fun getById(id: String): OpenCodeWorkspaceRefEntity?

    @Query("SELECT * FROM opencode_workspace_refs WHERE connection_id = :connectionId ORDER BY updated_at DESC")
    suspend fun listByConnectionId(connectionId: String): List<OpenCodeWorkspaceRefEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ref: OpenCodeWorkspaceRefEntity)

    @Query("DELETE FROM opencode_workspace_refs WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
