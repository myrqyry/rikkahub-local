package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_evidence",
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["type"]),
        Index(value = ["origin"]),
        Index(value = ["session_id"]),
    ],
)
data class EvidenceEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val id: String,
    val type: String,
    val payload: String,
    val origin: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
)
