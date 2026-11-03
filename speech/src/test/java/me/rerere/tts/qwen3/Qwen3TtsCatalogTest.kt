package me.rerere.tts.qwen3

import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import org.junit.Assert.*
import org.junit.Test

class Qwen3TtsCatalogTest {
    private val entry = Qwen3TtsCatalog.ENTRIES.single()

    @Test fun manifestIsRevisionPinnedAndMatchesSize() {
        assertEquals("d6ba751e42044ce443bab046a80a581076d9fc90",
            Qwen3TtsCatalogEntry.REVISION)
        assertEquals(10, entry.files.size)
        assertEquals(1_880_805_470L, entry.files.sumOf { it.bytes })
        assertEquals(entry.sizeBytes, entry.files.sumOf { it.bytes })
        assertEquals(10, entry.files.map { it.local }.distinct().size)
        assertTrue(entry.files.all {
            it.remote.startsWith("legacy/") && !it.remote.contains("..") &&
                it.sha256.matches(Regex("[0-9a-f]{64}")) && it.bytes > 0
        })
        assertTrue(entry.downloadPairs().all { (url, local) ->
            url.startsWith("https://huggingface.co/" + entry.modelId +
                "/resolve/" + Qwen3TtsCatalogEntry.REVISION + "/legacy/") &&
                local in entry.requiredFiles
        })
    }

    @Test fun originalEngineFilenamesStayCompatible() {
        assertEquals(
            listOf("talker_int4.tflite", "mtp_fp32.tflite",
                "codec_decoder_fp32.tflite", "codec_embedding_fp32.npy",
                "mtp_embeddings_fp16.npy", "text_embedding_fp16.npy",
                "text_projection_fp32.npz", "demo_speaker.npy",
                "vocab.json", "merges.txt"),
            entry.requiredFiles,
        )
        assertEquals("legacy/tables/text_embedding_fp16.npy",
            entry.remoteForLocal("text_embedding_fp16.npy"))
        assertNull(entry.remoteForLocal("qwen3_tts_int4.litertlm"))
    }

    @Test fun corruptedAndIncompleteFilesNeverPassVerification() {
        val temp = createTempDirectory("qwen3-test").toFile()
        try {
            val bytes = "correct input".toByteArray()
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val spec = Qwen3TtsCatalogFile(
                "legacy/example.tflite", "example.tflite", bytes.size.toLong(), hash,
            )
            assertFalse(entry.verifyFiles(temp, listOf(spec)))
            val target = File(temp, spec.local)
            target.writeBytes(bytes)
            assertTrue(entry.verifyFiles(temp, listOf(spec)))
            target.writeBytes("corrupt input".toByteArray())
            assertFalse(entry.verifyFiles(temp, listOf(spec)))
            assertFalse(entry.verifyFiles(temp, emptyList()))
            assertFalse(entry.isVerified(temp))
        } finally {
            temp.deleteRecursively()
        }
    }
}
