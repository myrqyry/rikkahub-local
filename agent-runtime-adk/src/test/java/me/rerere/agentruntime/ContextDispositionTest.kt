package me.rerere.agentruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextDispositionTest {

    @Test
    fun `byteSafePrefix keeps a valid utf8 prefix within budget`() {
        assertEquals("héllo", ContextDisposition.byteSafePrefix("héllo wörld", 6))
        assertEquals("", ContextDisposition.byteSafePrefix("abc", 0))
        assertEquals("", ContextDisposition.byteSafePrefix("abc", -1))
    }

    @Test
    fun `byteSafePrefix never splits a surrogate pair`() {
        val emoji = "\uD83D\uDE00" // U+1F600 (4 UTF-8 bytes, 2 UTF-16 units)
        val s = "a${emoji}b"
        assertEquals("a", ContextDisposition.byteSafePrefix(s, 3)) // 1 + 2 = fits at 'a'
        assertEquals("a$emoji", ContextDisposition.byteSafePrefix(s, 5)) // full pair fits
        assertTrue(ContextDisposition.byteSafePrefix(s, 2).toByteArray(Charsets.UTF_8).size <= 2)
    }

    @Test
    fun `charSafePrefix respects the char budget without a lone surrogate`() {
        val emoji = "\uD83D\uDE00"
        val s = "ab${emoji}c"
        assertEquals("ab", ContextDisposition.charSafePrefix(s, 2))
        assertEquals("ab$emoji", ContextDisposition.charSafePrefix(s, 4))
    }

    @Test
    fun `capBytes truncates to budget with ellipsis and never exceeds it`() {
        val capped = ContextDisposition.capBytes("hello world", 8)
        assertEquals("hello...", capped)
        assertTrue(capped.toByteArray(Charsets.UTF_8).size <= 8)
        assertEquals("short", ContextDisposition.capBytes("short", 10))
    }

    @Test
    fun `capBytes truncation marker fits budgets smaller than an ellipsis`() {
        for (budget in 0..2) {
            val capped = ContextDisposition.capBytes("hello", budget)
            assertEquals(ContextDisposition.byteSafePrefix("...", budget), capped)
            assertTrue(capped.toByteArray(Charsets.UTF_8).size <= budget)
        }
    }

    @Test
    fun `truncateValue appends a truncated marker`() {
        val big = "x".repeat(2000)
        val out = ContextDisposition.truncateValue(mapOf("data" to big), 100)
        assertTrue(out.length < big.length)
        assertTrue(out.contains("[truncated]"))
        assertTrue(out.toByteArray(Charsets.UTF_8).size <= 100)
    }

    @Test
    fun `truncateValue marker fits budgets smaller than the marker`() {
        val marker = "... [truncated]"
        for (budget in 0 until marker.toByteArray(Charsets.UTF_8).size) {
            val out = ContextDisposition.truncateValue("x".repeat(marker.length + 1), budget)
            assertEquals(ContextDisposition.byteSafePrefix(marker, budget), out)
            assertTrue(out.toByteArray(Charsets.UTF_8).size <= budget)
        }
    }

    @Test
    fun `continuation marker accumulates bytes across appends`() {
        val key = "session-1"
        val acc = ContinuationMarker()
        acc.append(key, 100)
        acc.append(key, 50)
        assertEquals(150L, acc.consume(key))
    }

    @Test
    fun `continuation marker consumes once and ignores non-positive counts`() {
        val key = "session-2"
        val acc = ContinuationMarker()
        acc.append(key, 0)
        acc.append(key, -5)
        acc.append(key, 200)
        assertEquals(200L, acc.consume(key))
        assertEquals(0L, acc.consume(key))
    }

    @Test
    fun `continuation marker is keyed per session`() {
        val acc = ContinuationMarker()
        acc.append("a", 10)
        acc.append("b", 20)
        assertEquals(10L, acc.consume("a"))
        assertEquals(20L, acc.consume("b"))
        assertEquals(0L, acc.consume("missing"))
    }
}
