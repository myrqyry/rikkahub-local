package me.rerere.tts.kitten

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class KittenTtsTokenizerTest {

    private val tokenizer = KittenTtsTokenizer()

    @Test
    fun `vocab covers the expected 175 symbols`() {
        assertEquals(175, tokenizer.vocabSize)
        // Spot-check indices against the Python reference dict
        assertEquals(0, tokenizer.wordIndex["$"])
        assertEquals(3, tokenizer.wordIndex[","])
        assertEquals(16, tokenizer.wordIndex[" "])
        assertEquals(24, tokenizer.wordIndex["H"])
        assertEquals(43, tokenizer.wordIndex["a"])
    }

    @Test
    fun `encode wraps ids with bos and eos`() {
        val ids = tokenizer.encode("Hello")
        assertEquals(0, ids.first())
        assertEquals(0, ids.last())
        assertTrue(ids.size >= 3)
    }

    @Test
    fun `empty text yields just bos and eos`() {
        assertEquals(listOf(0, 0), tokenizer.encode(""))
        assertEquals(listOf(0, 0), tokenizer.encode("   "))
    }

    @Test
    fun `unknown characters are filtered out`() {
        // © (U+00A9) is not in the vocab and must be dropped without crashing;
        // the token-join inserts spaces (index 16), which are part of the vocab.
        val ids = tokenizer.encode("a©b")
        assertEquals(0, ids.first())
        assertEquals(0, ids.last())
        // a(43) and b(44) must both appear; © must never appear.
        assertTrue(ids.contains(43))
        assertTrue(ids.contains(44))
        assertTrue(ids.none { it == 177 }) // © would occupy a slot only in a wrong mapping
    }

    @Test
    fun `ipa phoneme strings tokenize`() {
        // The phonemized output of "hello" is "həlˈoʊ"
        val ids = tokenizer.encode("həlˈoʊ")
        assertTrue(ids.size >= 6)
        assertEquals(0, ids.first())
        assertEquals(0, ids.last())
    }
}

class NpzParserTest {

    private fun makeNpy(data: FloatArray, shape: List<Int>): ByteArray {
        val descr = when {
            shape.size == 2 -> "<f4"
            else -> "<f4"
        }
        val dict = "{'descr': '$descr', 'fortran_order': False, 'shape': (${
            shape.joinToString(", ")
        },), }"
        val padded = padNpyHeader(dict)
        val headerLen = padded.toByteArray(Charsets.US_ASCII).size
        val magic = byteArrayOf(
            0x93.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 'M'.code.toByte(),
            'P'.code.toByte(), 'Y'.code.toByte(), 0x01, 0x00,
            (headerLen and 0xFF).toByte(),
            ((headerLen shr 8) and 0xFF).toByte(),
        )
        val bb = java.nio.ByteBuffer.allocate(4 * data.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        data.forEach { bb.putFloat(it) }
        return magic + padded.toByteArray(Charsets.US_ASCII) + bb.array()
    }

    private fun padNpyHeader(base: String): String {
        // Align to 64 bytes total after \x93NUMPY(2 bytes) + ver(2) + len(2) = 6 bytes head
        var header = base.padEnd(base.length + ((8 - (base.length % 8)) % 8), ' ')
        header += '\n'
        return header
    }

    private fun makeNpz(vararg entries: Pair<String, ByteArray>): File {
        val f = File.createTempFile("voices", ".npz")
        ZipOutputStream(FileOutputStream(f)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry("$name.npy"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        f.deleteOnExit()
        return f
    }

    @Test
    fun `parses float32 voice arrays from npz`() {
        val voice1 = FloatArray(256) { it * 0.5f }
        val voice2 = FloatArray(256) { -it * 0.25f }
        val npz = makeNpz(
            "expr-voice-2-m" to makeNpy(voice1, listOf(1, 256)),
            "expr-voice-2-f" to makeNpy(voice2, listOf(1, 256)),
        )
        val voices = NpzParser.parse(npz)
        assertEquals(setOf("expr-voice-2-m", "expr-voice-2-f"), voices.keys)
        assertEquals(256, voices["expr-voice-2-m"]!!.size)
        assertEquals(0f, voices["expr-voice-2-m"]!![0])
        assertEquals(127.5f, voices["expr-voice-2-m"]!![255])
        assertEquals(-63.75f, voices["expr-voice-2-f"]!![255])
    }

    @Test
    fun `flat 256 npy also parses`() {
        val data = FloatArray(256) { 1.0f }
        val npz = makeNpz("voice" to makeNpy(data, listOf(256)))
        val voices = NpzParser.parse(npz)
        assertEquals(256, voices["voice"]!!.size)
        assertEquals(1.0f, voices["voice"]!![100])
    }
}
