package me.rerere.rikkahub.data.ai.prompts

import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUtilityPromptTest {
    @Test fun targetedEditsPreserveOtherSettings() {
        val original = Settings(
            titlePrompt = "old title",
            translatePrompt = "old translation",
            suggestionPrompt = "old suggestion",
            ocrPrompt = "old OCR",
            compressPrompt = "old compression",
            translateThinkingBudget = 123,
        )
        for (kind in ChatUtilityPrompt.entries) {
            val revised = original.withUtilityPrompt(kind, "replacement")
            val values = listOf(revised.titlePrompt, revised.translatePrompt,
                revised.suggestionPrompt, revised.ocrPrompt, revised.compressPrompt)
            assertEquals("Only the selected prompt should change: $kind", 1,
                values.count { it == "replacement" })
            assertEquals(original.translateThinkingBudget, revised.translateThinkingBudget)
            assertEquals(original.copy(translateThinkingBudget = 0),
                revised.copy(translateThinkingBudget = 0).withUtilityPrompt(kind,
                    when (kind) {
                        ChatUtilityPrompt.TITLE -> original.titlePrompt
                        ChatUtilityPrompt.TRANSLATION -> original.translatePrompt
                        ChatUtilityPrompt.SUGGESTION -> original.suggestionPrompt
                        ChatUtilityPrompt.OCR -> original.ocrPrompt
                        ChatUtilityPrompt.COMPRESSION -> original.compressPrompt
                    }))
        }
    }
}
