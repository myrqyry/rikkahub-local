package me.rerere.tts.pocket

import org.junit.Assert.assertEquals
import org.junit.Test

class PocketTtsTokenizerTest {

    private val vocabulary = """
{
  "<unk>": 0,
  "\u2581hello": 1,
  "\u2581world": 2,
  ".": 3,
  "\u2581": 4,
  "a": 5,
  "\u2581a": 6,
  "<0xC3>": 7,
  "<0xA9>": 8
}
"""

    private val scores = """
{
  "<unk>": -100.0,
  "\u2581hello": -1.0,
  "\u2581world": -1.0,
  ".": -0.1,
  "\u2581": -0.1,
  "a": -0.1,
  "\u2581a": -1.0,
  "<0xC3>": -2.0,
  "<0xA9>": -2.0
}
"""

    @Test
    fun `words and whitespace tokenize to whole tokens`() {
        val tokenizer = PocketTtsTokenizer(vocabulary, scores)
        assertEquals(listOf(1, 2, 3), tokenizer.encodeIds("hello world."))
    }

    @Test
    fun `unigram scores choose best path`() {
        val tokenizer = PocketTtsTokenizer(vocabulary, scores)
        // Split marker + character scores -0.2, which beats the -1.0 whole token.
        assertEquals(listOf(4, 5), tokenizer.encodeIds("a"))
    }

    @Test
    fun `unknown utf8 bytes fall back to byte tokens`() {
        val tokenizer = PocketTtsTokenizer(vocabulary, scores)
        assertEquals(listOf(4, 7, 8), tokenizer.encodeIds("\u00E9"))
    }
}
