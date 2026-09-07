package me.rerere.rikkahub.data.opencode

import kotlinx.serialization.Serializable

@Serializable
data class OpenCodeHealth(val healthy: Boolean, val version: String? = null)

@Serializable
data class OpenCodeProject(
    val id: String? = null,
    val directory: String,
    val name: String? = null,
)

@Serializable
data class OpenCodeSession(
    val id: String,
    val title: String? = null,
    val directory: String? = null,
)

@Serializable
data class OpenCodeMessage(val id: String, val role: String, val text: String)
