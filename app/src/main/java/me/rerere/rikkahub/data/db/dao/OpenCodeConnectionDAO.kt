package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.OpenCodeConnectionEntity

@Dao
interface OpenCodeConnectionDAO {
    @Query("SELECT * FROM opencode_connections ORDER BY updated_at DESC")
    fun listFlow(): Flow<List<OpenCodeConnectionEntity>>

    @Query("SELECT * FROM opencode_connections WHERE id = :id")
    suspend fun getById(id: String): OpenCodeConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(connection: OpenCodeConnectionEntity)

    @Query("DELETE FROM opencode_connections WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
