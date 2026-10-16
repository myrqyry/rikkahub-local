package me.rerere.locallm

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFileImportTest {
    private val original = "GGUF".toByteArray() + ByteArray(256) { 0x11 }
    private val newer = "GGUF".toByteArray() + ByteArray(256) { 0x22 }
    private fun withTarget(test: (File) -> Unit) {
        val dir = Files.createTempDirectory("model-import-test-").toFile()
        try { test(File(dir, "user-model.gguf")) } finally { dir.deleteRecursively() }
    }

    @Test fun importsValidFileAfterCompleteCopy() = withTarget { target ->
        ModelFileImport.copyVerified(ByteArrayInputStream(newer), target, "gguf")
        assertArrayEquals(newer, target.readBytes())
        assertFalse(target.parentFile!!.listFiles()!!.any { it.name.endsWith(".partial") })
    }

    @Test fun acceptsLiteRtHeaderButDoesNotClaimInference() = withTarget { ggufTarget ->
        val target = File(ggufTarget.parentFile, "model.litertlm")
        val bytes = "LITERTLM".toByteArray() + ByteArray(128) { 3 }
        ModelFileImport.copyVerified(ByteArrayInputStream(bytes), target, "litertlm")
        assertArrayEquals(bytes, target.readBytes())
    }

    @Test fun existingUserModelNeverGetsOverwritten() = withTarget { target ->
        target.writeBytes(original)
        assertThrows(IllegalStateException::class.java) {
            ModelFileImport.copyVerified(ByteArrayInputStream(newer), target, "gguf")
        }
        assertArrayEquals(original, target.readBytes())
        assertFalse(target.parentFile!!.listFiles()!!.any { it.name.endsWith(".partial") })
    }

    @Test fun invalidModelHeaderCannotBePromoted() = withTarget { target ->
        assertThrows(IllegalArgumentException::class.java) {
            ModelFileImport.copyVerified(ByteArrayInputStream("<html>error</html>".toByteArray()), target, "gguf")
        }
        assertFalse(target.exists())
        assertTrue(target.parentFile!!.listFiles()!!.isEmpty())
    }

    @Test fun truncatedSourceDoesNotCreateOrModifyTarget() = withTarget { target ->
        val stream = object : ByteArrayInputStream(newer) {
            private var reads = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (reads++ > 0) throw IOException("Source URI disconnected halfway")
                return super.read(b, off, 5.coerceAtMost(len))
            }
        }
        assertThrows(IOException::class.java) {
            ModelFileImport.copyVerified(stream, target, "gguf")
        }
        assertFalse(target.exists())
        assertTrue(target.parentFile!!.listFiles()!!.isEmpty())
    }

    @Test fun classifyMoveRefusesExistingChatModel() = withTarget { target ->
        val moved = File(target.parentFile, "chat-model.gguf")
        target.writeBytes(original)
        moved.writeBytes(newer)
        assertThrows(IllegalStateException::class.java) {
            ModelFileImport.moveWithoutReplacing(moved, target)
        }
        assertArrayEquals(original, target.readBytes())
        assertArrayEquals(newer, moved.readBytes())
    }

    @Test fun concurrentTargetArrivalNeverGetsOverwritten() = withTarget { target ->
        val data = original
        val stream = object : ByteArrayInputStream(newer) {
            private var arrived = false
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!arrived) {
                    arrived = true
                    target.writeBytes(data)
                }
                return super.read(b, off, len)
            }
        }
        assertThrows(Exception::class.java) {
            ModelFileImport.copyVerified(stream, target, "gguf")
        }
        assertArrayEquals(data, target.readBytes())
    }

    @Test fun sourceStreamIsClosedWhenTargetExists() = withTarget { target ->
        target.writeBytes(original)
        var closed = false
        val stream = object : ByteArrayInputStream(newer) {
            override fun close() { closed = true; super.close() }
        }
        assertThrows(IllegalStateException::class.java) {
            ModelFileImport.copyVerified(stream, target, "gguf")
        }
        assertTrue(closed)
    }

    @Test fun cancellationDuringCopyRemovesOnlyStagingFile() = withTarget { target ->
        val bytes = "GGUF".toByteArray() + ByteArray(200_000) { 1 }
        var checkpoints = 0
        assertThrows(CancellationException::class.java) {
            ModelFileImport.copyVerified(ByteArrayInputStream(bytes), target, "gguf") {
                if (++checkpoints >= 3) throw CancellationException("User stopped import")
            }
        }
        assertFalse(target.exists())
        assertTrue(target.parentFile!!.listFiles()!!.isEmpty())
    }

    @Test fun targetSymlinkCannotBeReplaced() = withTarget { target ->
        val other = File(target.parentFile, "previous.gguf")
        other.writeBytes(original)
        Files.createSymbolicLink(target.toPath(), other.toPath())
        assertThrows(IllegalStateException::class.java) {
            ModelFileImport.copyVerified(ByteArrayInputStream(newer), target, "gguf")
        }
        assertArrayEquals(original, other.readBytes())
        assertTrue(Files.isSymbolicLink(target.toPath()))
    }

    @Test fun repeatedImportPreservesTheFirstModel() = withTarget { target ->
        ModelFileImport.copyVerified(ByteArrayInputStream(original), target, "gguf")
        assertThrows(IllegalStateException::class.java) {
            ModelFileImport.copyVerified(ByteArrayInputStream(newer), target, "gguf")
        }
        assertArrayEquals(original, target.readBytes())
    }
}
