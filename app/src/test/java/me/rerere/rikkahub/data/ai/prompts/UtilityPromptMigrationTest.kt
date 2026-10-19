package me.rerere.rikkahub.data.ai.prompts

import org.junit.Assert.*
import org.junit.Test

class UtilityPromptMigrationTest {
    @Test fun unchangedOldDefaultsUpgradeButCustomPromptsSurvive() {
        val cases = listOf(
            Triple(LEGACY_TITLE_PROMPT, DEFAULT_TITLE_PROMPT, "{content}"),
            Triple(LEGACY_SUGGESTION_PROMPT, DEFAULT_SUGGESTION_PROMPT, "{locale}"),
            Triple(LEGACY_TRANSLATION_PROMPT, DEFAULT_TRANSLATION_PROMPT, "{target_lang}"),
            Triple(LEGACY_OCR_PROMPT, DEFAULT_OCR_PROMPT, "reading order"),
            Triple(LEGACY_COMPRESS_PROMPT, DEFAULT_COMPRESS_PROMPT, "{target_tokens}"),
        )
        for ((old, newest, required) in cases) {
            assertNotEquals(old, newest)
            assertEquals(newest, migrateUtilityPrompt(null, old, newest))
            assertEquals(newest, migrateUtilityPrompt(old, old, newest))
            assertEquals("Custom text should not migrate", "mine",
                migrateUtilityPrompt("mine", old, newest))
            assertEquals(newest, migrateUtilityPrompt(newest, old, newest))
            assertTrue(newest.contains(required))
        }
    }

    @Test fun promptDefaultsKeepAllRequiredVariables() {
        for (token in listOf("{content}", "{locale}")) {
            assertTrue(DEFAULT_TITLE_PROMPT.contains(token))
            assertTrue(DEFAULT_SUGGESTION_PROMPT.contains(token))
        }
        for (token in listOf("{content}", "{target_tokens}", "{additional_context}", "{locale}"))
            assertTrue(DEFAULT_COMPRESS_PROMPT.contains(token))
        assertTrue(DEFAULT_TRANSLATION_PROMPT.contains("{source_text}"))
        assertTrue(DEFAULT_TRANSLATION_PROMPT.contains("{target_lang}"))
        assertFalse(DEFAULT_OCR_PROMPT.contains("{images}"))
    }
}
