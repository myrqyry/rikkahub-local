package me.rerere.rikkahub.data.db.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ZeroProcedureDao {
    @Query("SELECT * FROM zero_procedures")
    fun observeAll(): Flow<List<ZeroProcedureEntity>>

    @Query("SELECT * FROM zero_procedures")
    suspend fun listAll(): List<ZeroProcedureEntity>

    @Query("SELECT * FROM zero_procedures WHERE id = :id")
    suspend fun getById(id: String): ZeroProcedureEntity?

    @Query("SELECT * FROM zero_procedures WHERE id = :id")
    fun observeById(id: String): Flow<ZeroProcedureEntity?>

    @Query("SELECT * FROM zero_procedures WHERE source = :source")
    suspend fun listBySource(source: String): List<ZeroProcedureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ZeroProcedureEntity)

    @Update
    suspend fun update(entity: ZeroProcedureEntity)

    @Query("DELETE FROM zero_procedures WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
