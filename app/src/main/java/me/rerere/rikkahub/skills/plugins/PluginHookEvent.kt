package me.rerere.rikkahub.skills.plugins

sealed interface PluginHookEvent {
    data class UserPromptSubmit(
        val conversationId: String,
        val contentPreview: String,
    ) : PluginHookEvent

    data class PreToolUse(
        val conversationId: String?,
        val toolName: String,
        val args: String,
    ) : PluginHookEvent

    data class PostToolUse(
        val conversationId: String?,
        val toolName: String,
        val args: String,
        val resultPreview: String,
    ) : PluginHookEvent

    data class Stop(
        val conversationId: String,
    ) : PluginHookEvent

    data class SessionEnd(
        val conversationId: String,
    ) : PluginHookEvent
}
