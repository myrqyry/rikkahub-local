package me.rerere.rikkahub.data.ai.prompts

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import kotlin.uuid.Uuid
import org.junit.Assert.*
import org.junit.Test

class CompressionSafetyTest {
    @Test fun originalConversationGetsDistinctRecoverableArchive() {
        val msg = UIMessage.user("User decided to retain context")
        val input = Conversation(
            title = "Working session",
            assistantId = Uuid.random(),
            messageNodes = listOf(msg.toMessageNode()),
            customSystemPrompt = "remain precise",
        )
        val backup = input.preCompressionBackup { it }
        assertNotEquals(input.id, backup.id)
        assertEquals("Before compression — Working session", backup.title)
        assertEquals(input.assistantId, backup.assistantId)
        assertEquals(input.customSystemPrompt, backup.customSystemPrompt)
        assertEquals(input.messageNodes[0].messages, backup.messageNodes[0].messages)
        assertNotEquals(input.messageNodes[0].id, backup.messageNodes[0].id)
        assertFalse(backup.isPinned)
        assertEquals(0L, backup.revision)
        assertEquals("Working session", input.title)
        assertEquals(1, input.messageNodes.size)
    }

    @Test fun compressionInputContainsEvidenceMarkersRatherThanDroppingAttachments() {
        val msg = UIMessage(
            role = MessageRole.USER,
            parts = listOf(
                UIMessagePart.Text("Original decision: preserve this."),
                UIMessagePart.Image("data:image/png;base64,SECRET"),
                UIMessagePart.Document("file:///tmp/receipt.pdf", "receipt.pdf", "application/pdf"),
            ),
        )
        val summary = msg.toCompressionSource()
        assertTrue(summary.contains("Original decision"))
        assertTrue(summary.contains("[Image attached"))
        assertTrue(summary.contains("[embedded data]"))
        assertTrue(summary.contains("receipt.pdf"))
        assertFalse(summary.contains("SECRET"))
        assertTrue(summary.contains("file content not included"))
    }

    @Test fun emptyCompressionResultIsRejectedWithoutSyntheticContent() {
        for (bad in listOf<String?>(null, "", "  \n ")) {
            assertThrows(IllegalArgumentException::class.java) {
                checkedCompressedSummary(bad)
            }
        }
        assertEquals("Verified facts only", checkedCompressedSummary("  Verified facts only  "))
    }

    @Test fun normalizationRejectsDuplicateSuggestionsAndBlankTitles() {
        assertNull(generatedConversationTitle("  "))
        assertEquals("Actual subject", generatedConversationTitle("\"Actual subject\"\nextra line"))
        assertEquals(listOf("Can we test it?", "Let's compare both", "What changed?"),
            generatedReplySuggestions(
                "1. Can we test it?\n2. Let's compare both\n- Can we test it?\nWhat changed?"
            ))
    }
}
