package me.rerere.rikkahub.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatChromeActionsTest {
    @Test fun overflowContainsConversationSearchAndCompressNotGlobalSearchOrNewChat() {
        assertEquals(
            listOf(ChatOverflowAction.SEARCH_IN_CONVERSATION, ChatOverflowAction.COMPRESS_HISTORY),
            ChatOverflowAction.entries,
        )
    }

    @Test fun headingNeverMirrorsSelectedProviderOrModel() {
        assertEquals("New Chat", chatHeaderTitle("", "New Chat"))
        assertEquals("Review ideas", chatHeaderTitle("Review ideas", "New Chat"))
        assertFalse(chatHeaderTitle("", "New Chat").contains("GPT"))
    }
}
