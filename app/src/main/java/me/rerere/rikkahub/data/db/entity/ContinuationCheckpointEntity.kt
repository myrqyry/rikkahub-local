package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "continuation_checkpoints",
    indices = [
        Index(value = ["run_id"]),
        Index(value = ["run_id", "sequence"], unique = true),
    ],
)
data class ContinuationCheckpointEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "run_id") val runId: String,
    val sequence: Long,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "verified_at_ms") val verifiedAtMs: Long,
    @ColumnInfo(name = "snapshot_json") val snapshotJson: String,
)
