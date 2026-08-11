package me.rerere.rikkahub.data.rag

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "vector_store")
data class VectorEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "embedding") val embedding: ByteArray,
    @ColumnInfo(name = "metadata") val metadata: String,
    @ColumnInfo(name = "embeddingSpaceId", defaultValue = "legacy") val embeddingSpaceId: String = "legacy",
    @ColumnInfo(name = "embeddingDimension", defaultValue = "0") val embeddingDimension: Int = 0,
)

@Dao
interface VectorDao {
    @Query("SELECT * FROM vector_store")
    suspend fun getAll(): List<VectorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VectorEntity)

    @Query("DELETE FROM vector_store WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM vector_store")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM vector_store")
    suspend fun count(): Int
}
