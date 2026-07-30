package me.rerere.rikkahub.skills.plugins

import kotlinx.serialization.Serializable

@Serializable
data class InstalledPlugin(
    val name: String,
    val manifestVersion: String,
    val description: String,
    val author: String,
    val enabled: Boolean = true,
    val sourceUrl: String,
)
