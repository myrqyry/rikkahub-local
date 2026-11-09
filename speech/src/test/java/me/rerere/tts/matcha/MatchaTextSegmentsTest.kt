package me.rerere.tts.matcha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchaTextSegmentsTest {
    @Test
    fun `short text stays intact as one segment`() {
        assertEquals(listOf("Hello there."), splitMatchaText("  Hello there.  "))
    }

    @Test
    fun `long input splits before fixed model limit without losing words`() {
        val text = "Hello there. This is a test of the local Matcha speech engine. " +
            "It needs to pronounce every word without losing the last sentence."
        val segments = splitMatchaText(text)
        assertTrue("Expected multiple segments", segments.size > 1)
        assertTrue(segments.all { it.isNotBlank() && it.length <= 48 })
        assertEquals(text, segments.joinToString(" "))
    }

    @Test
    fun `single oversized token must not disappear or exceed fixed segment size`() {
        val word = "abcdefghijklmnopqrstuvwxyz".repeat(4)
        val segments = splitMatchaText(word)
        assertTrue(segments.size > 1)
        assertEquals(word, segments.joinToString(""))
        assertTrue(segments.all { it.length <= 48 })
    }

    @Test
    fun `only whitespace yields no audio segments`() {
        assertTrue(splitMatchaText("  \n\t ").isEmpty())
    }
}
