package me.rerere.locallm.llamacpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaSessionContinuationTest {

    @Test
    fun `growing history with matching prefix continues`() {
        val stored = listOf("hi", "Hello!")
        val incoming = listOf("hi", "Hello!", "what is 2+2?")
        assertTrue(LlamaSessionContinuation.shouldContinue(stored, incoming))
        assertEquals("what is 2+2?", LlamaSessionContinuation.continuationText(incoming))
    }

    @Test
    fun `same size history does not continue`() {
        val stored = listOf("hi", "Hello!")
        val incoming = listOf("hi", "Hello!")
        assertFalse(LlamaSessionContinuation.shouldContinue(stored, incoming))
    }

    @Test
    fun `diverged history resets`() {
        val stored = listOf("hi", "Hello!")
        val incoming = listOf("hey", "Hi there!", "what is 2+2?")
        assertFalse(LlamaSessionContinuation.shouldContinue(stored, incoming))
    }

    @Test
    fun `history that grew by more than one message resets`() {
        val stored = listOf("hi")
        val incoming = listOf("hi", "Hello!", "what is 2+2?")
        assertFalse(LlamaSessionContinuation.shouldContinue(stored, incoming))
    }

    @Test
    fun `empty stored history does not continue`() {
        val incoming = listOf("hi", "Hello!")
        assertFalse(LlamaSessionContinuation.shouldContinue(emptyList<String>(), incoming))
    }
}
