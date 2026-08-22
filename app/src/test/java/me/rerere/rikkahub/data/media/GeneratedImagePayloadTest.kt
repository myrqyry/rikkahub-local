package me.rerere.rikkahub.data.media

import me.rerere.ai.ui.GeneratedImagePayload
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase: image-gen refinement Task 3. [writePayloadToFile] writes a payload's bytes to disk
 * without a base64 round-trip for Bytes/File payloads, and only decodes for the Base64 case
 * (cloud path). Uses a pure seam (no Android Context) so it is JVM-testable.
 */
class GeneratedImagePayloadTest {

    private fun tempDir(): File = File.createTempFile("payload-test", "").also {
        it.delete()
        it.mkdirs()
    }

    @Test
    fun `file payload round-trips without a base64 step`() {
        val dir = tempDir()
        try {
            val source = File(dir, "source.png")
            source.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            val target = File(dir, "target.png")
            writePayloadToFile(GeneratedImagePayload.File(source.absolutePath, "image/png"), target)
            assertEquals(source.readBytes().toList(), target.readBytes().toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `bytes payload writes raw bytes`() {
        val dir = tempDir()
        try {
            val target = File(dir, "target.png")
            val raw = byteArrayOf(9, 8, 7, 6)
            writePayloadToFile(GeneratedImagePayload.Bytes(raw, "image/png"), target)
            assertEquals(raw.toList(), target.readBytes().toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `base64 payload decodes on write`() {
        val dir = tempDir()
        try {
            val target = File(dir, "target.png")
            val raw = byteArrayOf(10, 20, 30)
            writePayloadToFile(
                GeneratedImagePayload.Base64(Base64.getEncoder().encodeToString(raw), "image/png"),
                target,
            )
            assertEquals(raw.toList(), target.readBytes().toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `base64 payload strips data uri prefix before decoding`() {
        val dir = tempDir()
        try {
            val target = File(dir, "target.png")
            val raw = byteArrayOf(1, 0, 1, 0)
            writePayloadToFile(
                GeneratedImagePayload.Base64(
                    "data:image/png;base64," + Base64.getEncoder().encodeToString(raw),
                    "image/png",
                ),
                target,
            )
            assertEquals(raw.toList(), target.readBytes().toList())
        } finally {
            dir.deleteRecursively()
        }
    }
}
