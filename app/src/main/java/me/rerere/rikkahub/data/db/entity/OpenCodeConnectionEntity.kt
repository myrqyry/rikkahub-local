package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "opencode_connections", indices = [Index(value = ["updated_at"])])
data class OpenCodeConnectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    @ColumnInfo(name = "auth_secret_ref") val authSecretRef: String? = null,
    @ColumnInfo(name = "last_health_status") val lastHealthStatus: String = "UNKNOWN",
    @ColumnInfo(name = "server_version") val serverVersion: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
