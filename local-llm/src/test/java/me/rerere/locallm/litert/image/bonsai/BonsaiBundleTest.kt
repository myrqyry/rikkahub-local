package me.rerere.locallm.litert.image.bonsai

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import kotlin.io.path.createTempDirectory

class BonsaiBundleTest {
    @Test fun emptyInstallIsNotReady() {
        val dir = createTempDirectory("bonsai-empty").toFile()
        try {
            assertEquals(BonsaiBundle.files.map { it.relative }, BonsaiBundlePackage(dir).missingFiles())
            val e = assertThrows(IllegalArgumentException::class.java) {
                BonsaiBundlePackage(dir).requireVerified()
            }
            assertTrue(e.message!!.contains("textenc_int4.tflite"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun wrongSizeAndSymlinkCannotQualifyAsModels() {
        val dir = createTempDirectory("bonsai-size").toFile()
        try {
            val spec = BonsaiBundle.files.first()
            val file = File(dir, spec.relative)
            file.writeText("not a valid model")
            assertTrue(BonsaiBundlePackage(dir).missingFiles().contains(spec.relative))
            assertFalse(BonsaiBundle.validSha256(file, spec))
        } finally { dir.deleteRecursively() }
    }

    @Test fun hashMustMatchPinnedBytesNotJustNameAndSize() {
        val dir = createTempDirectory("bonsai-digest").toFile()
        try {
            val f = File(dir, "tiny.bin").apply { writeText("verified") }
            val hash = MessageDigest.getInstance("SHA-256").digest(f.readBytes())
                .joinToString("") { "%02x".format(it) }
            val good = BonsaiBundle.FileSpec("tiny.bin", f.length(), hash)
            assertTrue(BonsaiBundle.validSha256(f, good))
            assertFalse(BonsaiBundle.validSha256(f, good.copy(sha256 = "0".repeat(64))))
            assertFalse(BonsaiBundle.validSha256(f, good.copy(bytes = f.length() + 1)))
        } finally { dir.deleteRecursively() }
    }

    @Test fun modelUrlsAreRevisionPinnedAndExternal() {
        assertEquals(5, BonsaiBundle.files.size)
        assertTrue(BonsaiBundle.files.all {
            it.url.contains("/resolve/${BonsaiBundle.REVISION}/") &&
                it.sha256.matches(Regex("[0-9a-f]{64}"))
        })
        assertFalse(BonsaiBundle.files.any { it.relative.contains("..") })
    }
    @Test fun pinnedMetadataNamesThePublished256Graphs() {
        val metaFile = File("src/main/assets/bonsai/pipeline_meta.json")
        assertTrue("Pinned reference metadata missing from module assets", metaFile.isFile)
        val meta = JSONObject(metaFile.readText())
        assertEquals(
            "dit_256_int4b32.tflite" to "vae_dec_256_fp32.tflite",
            BonsaiPipeline.graphFiles(meta, 256),
        )
        assertEquals("textenc_int4.tflite", meta.getJSONObject("files").getString("textenc"))
        assertEquals(256, BonsaiBundle.OUTPUT_SIZE)
        assertEquals(listOf(256, 512), BonsaiPipeline.sizes(meta))
    }

}
