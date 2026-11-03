package me.rerere.rikkahub.data.repository

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UntitledConversationPreviewTest {
    @Test
    fun firstUserTextBecomesTemporaryLabel() {
        assertEquals(
            "How can I build a shader?",
            titlePreviewFromMessages(listOf(UIMessage.user("  How can I build\n a shader?  ")))
        )
    }

    @Test
    fun noUsableTextFallsBackToTimestamp() {
        assertNull(titlePreviewFromMessages(emptyList()))
        assertNull(titlePreviewFromMessages(listOf(UIMessage.user("   "))))
    }

    @Test
    fun longTextIsTrimmedForCompactList() {
        val label = titlePreviewFromMessages(listOf(UIMessage.user("Z".repeat(150))))
        assertEquals("Z".repeat(90), label)
    }
}
