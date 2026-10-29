package me.rerere.rikkahub.ui.pages.chat

/** Menu actions operate only on the currently displayed conversation. */
internal enum class ChatOverflowAction {
    SEARCH_IN_CONVERSATION,
    COMPRESS_HISTORY,
}

/** Provider/model belong in the composer selector, not duplicated in the title. */
internal fun chatHeaderTitle(conversationTitle: String, fallback: String): String =
    conversationTitle.ifBlank { fallback }
