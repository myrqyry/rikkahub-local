package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "opencode_workspace_refs",
    foreignKeys = [ForeignKey(
        entity = OpenCodeConnectionEntity::class,
        parentColumns = ["id"],
        childColumns = ["connection_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["connection_id"])],
)
data class OpenCodeWorkspaceRefEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "connection_id") val connectionId: String,
    val name: String,
    @ColumnInfo(name = "remote_directory") val remoteDirectory: String,
    @ColumnInfo(name = "remote_project_id") val remoteProjectId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
