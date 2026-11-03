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

/** Pending requests are read from OpenCode; they do not themselves grant any capability. */
data class OpenCodePermission(
    val id: String,
    val sessionId: String,
    val permission: String,
    val patterns: List<String>,
)

/** Intentionally no persistent 'always' grant through the mobile bridge. */
enum class OpenCodePermissionReply(val wireValue: String) {
    ONCE("once"), REJECT("reject"),
}

sealed interface OpenCodeSessionSignal {
    data object Connected : OpenCodeSessionSignal
    data object MessagesChanged : OpenCodeSessionSignal
    data object StatusChanged : OpenCodeSessionSignal
    data object PermissionsChanged : OpenCodeSessionSignal
    data class Disconnected(val reason: String) : OpenCodeSessionSignal
}
