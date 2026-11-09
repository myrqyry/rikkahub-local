package me.rerere.rikkahub.ui.pages.setting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SpeechMenuSelectionTest {
    private val first = Uuid.parse("00000000-0000-0000-0000-000000000101")
    private val second = Uuid.parse("00000000-0000-0000-0000-000000000102")
    private val removed = Uuid.parse("00000000-0000-0000-0000-000000000103")

    @Test
    fun `configured speech provider stays selected even when listed second`() {
        val selection = resolveSpeechProviderSelection(second, listOf(first, second))
        assertEquals(second, selection.effectiveId)
        assertFalse(selection.isFallback)
    }

    @Test
    fun `missing configured provider matches runtime first-provider fallback without mutating settings`() {
        val selection = resolveSpeechProviderSelection(removed, listOf(first, second))
        assertEquals(first, selection.effectiveId)
        assertTrue(selection.isFallback)
    }

    @Test
    fun `unset ASR provider uses first available provider but is not a missing-selection warning`() {
        val selection = resolveSpeechProviderSelection(null, listOf(first, second))
        assertEquals(first, selection.effectiveId)
        assertFalse(selection.isFallback)
    }

    @Test
    fun `empty provider list has no active selection`() {
        val selection = resolveSpeechProviderSelection(removed, emptyList())
        assertNull(selection.effectiveId)
        assertFalse(selection.isFallback)
    }
}
