package me.rerere.rikkahub.data.ai.memory

import kotlinx.serialization.Serializable

@Serializable
data class MemEntry(
    val id: String,
    val type: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)
