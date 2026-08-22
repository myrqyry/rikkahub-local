package me.rerere.rikkahub.data.db.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EvidenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: EvidenceEntity): Long

    @Query("SELECT * FROM agent_evidence WHERE id = :id")
    suspend fun getById(id: String): EvidenceEntity?

    @Query(
        "SELECT * FROM agent_evidence " +
            "WHERE (:type IS NULL OR type = :type) " +
            "AND (:origin IS NULL OR origin = :origin) " +
            "AND (:sessionId IS NULL OR session_id = :sessionId) " +
            "ORDER BY sequence ASC",
    )
    suspend fun query(type: String?, origin: String?, sessionId: String?): List<EvidenceEntity>
}
