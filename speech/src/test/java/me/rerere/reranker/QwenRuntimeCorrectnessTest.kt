package me.rerere.reranker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenRuntimeCorrectnessTest {
    @Test
    fun empty_embedding_input_is_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            QwenEmbedder.requireTokenCount(0)
        }
    }

    @Test
    fun reranker_truncation_preserves_suffix() {
        val prompt = buildTruncatedPrompt(
            prefix = intArrayOf(1, 2),
            document = IntArray(10) { it + 10 },
            suffix = intArrayOf(90, 91),
            maxTokens = 6,
        )

        assertEquals(6, prompt.size)
        assertEquals(90, prompt[prompt.lastIndex - 1])
        assertEquals(91, prompt.last())
        assertTrue(prompt.sliceArray(2 until 4).contentEquals(intArrayOf(10, 11)))
    }
}
