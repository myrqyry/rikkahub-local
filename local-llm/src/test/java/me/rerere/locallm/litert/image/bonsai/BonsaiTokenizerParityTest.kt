package me.rerere.locallm.litert.image.bonsai

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Runs the real upstream 26-case Python-tokenizer golden when its small assets
 * are locally staged; no network access during builds, no synthetic tokenizer. */
class BonsaiTokenizerParityTest {
    @Test fun matchesUpstreamTokenizerGoldenIfAvailable() {
        val dir = File(System.getenv("BONSAI_FIXTURES")
            ?: "/tmp/bonsai-litert-reference-20261019/fixtures")
        val vocab = File(dir, "vocab.json")
        val merges = File(dir, "merges.txt")
        val golden = File(dir, "tok_golden.json")
        assumeTrue("Golden tokenizer fixtures not staged", listOf(vocab, merges, golden).all { it.isFile })
        val tokenizer = QwenTokenizer(vocab.inputStream(), merges.inputStream())
        val cases = JSONObject(golden.readText()).getJSONArray("cases")
        assertEquals(26, cases.length())
        for (i in 0 until cases.length()) {
            val entry = cases.getJSONObject(i)
            val encoded = tokenizer.encode("user\n" + entry.getString("prompt"))
            val expected = entry.getJSONArray("body_ids")
            assertArrayEquals("case $i", IntArray(expected.length()) { expected.getInt(it) }, encoded)
        }
    }
}
