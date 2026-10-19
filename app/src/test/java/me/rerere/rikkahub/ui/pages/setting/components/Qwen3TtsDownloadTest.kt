package me.rerere.rikkahub.ui.pages.setting.components

import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import me.rerere.tts.qwen3.Qwen3TtsCatalog
import me.rerere.tts.qwen3.Qwen3TtsCatalogFile
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Qwen3TtsDownloadTest {
    private val entry = Qwen3TtsCatalog.ENTRIES.single()
    private val data = byteArrayOf(16, 0, 0, 0) + "TFL3".toByteArray() +
        ByteArray(512) { (it % 251).toByte() }
    private val second = byteArrayOf(16, 0, 0, 0) + "TFL3".toByteArray() +
        ByteArray(128) { (it % 127).toByte() }
    private fun spec(name: String, bytes: ByteArray): Qwen3TtsCatalogFile =
        Qwen3TtsCatalogFile(
            "legacy/" + name, name, bytes.size.toLong(),
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) },
        )
    private fun withDir(block: (File) -> Unit) {
        val dir = createTempDirectory("qwen-download").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
    private fun client(
        expected: Map<String, ByteArray>,
        requests: MutableList<String>,
    ): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val name = expected.keys.single { request.url.encodedPath.endsWith("/" + it) }
        val bytes = expected.getValue(name)
        val offset = request.header("Range")?.removePrefix("bytes=")
            ?.removeSuffix("-")?.toIntOrNull() ?: 0
        requests += name + " from " + offset
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(if (offset > 0) 206 else 200)
            .message("test")
            .header("Content-Length", (bytes.size - offset).toString())
            .apply {
                if (offset > 0) header("Content-Range",
                    "bytes " + offset + "-" + (bytes.size - 1) + "/" + bytes.size)
            }
            .body(bytes.copyOfRange(offset, bytes.size).toResponseBody())
            .build()
    }.build()

    @Test fun downloadsVerifiedFilesAndSkipsExistingGoodFiles() = withDir { dir ->
        val a = spec("first.tflite", data)
        val b = spec("second.tflite", second)
        File(dir, a.local).writeBytes(data)
        val requests = mutableListOf<String>()
        var progress = -1
        runBlocking {
            Qwen3TtsDownload.downloadInto(
                dir, client(mapOf(a.local to data, b.local to second), requests),
                entry, listOf(a, b), 1_000_000_000L,
                onProgress = { progress = it },
            )
        }
        assertEquals(listOf("second.tflite from 0"), requests)
        assertEquals(100, progress)
        assertArrayEquals(second, File(dir, b.local).readBytes())
        assertTrue(entry.verifyFiles(dir, listOf(a, b)))
    }

    @Test fun resumedPinnedRangeTransfersOnlyMissingBytes() = withDir { dir ->
        val a = spec("resume.tflite", data)
        File(dir, a.local + ".partial").writeBytes(data.copyOfRange(0, 125))
        val requests = mutableListOf<String>()
        runBlocking {
            Qwen3TtsDownload.downloadInto(
                dir, client(mapOf(a.local to data), requests),
                entry, listOf(a), 1_000_000_000L,
            )
        }
        assertEquals(listOf("resume.tflite from 125"), requests)
        assertArrayEquals(data, File(dir, a.local).readBytes())
    }

    @Test fun existingCorruptedUserFileNeverGetsReplaced() = withDir { dir ->
        val a = spec("protect.tflite", data)
        val original = data.copyOf().also { it[it.size - 1] = 99 }
        File(dir, a.local).writeBytes(original)
        val requests = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                Qwen3TtsDownload.downloadInto(
                    dir, client(mapOf(a.local to data), requests), entry,
                    listOf(a), 1_000_000_000L,
                )
            }
        }
        assertTrue(requests.isEmpty())
        assertArrayEquals(original, File(dir, a.local).readBytes())
    }

    @Test fun wrongDownloadedChecksumCannotBecomeInstalled() = withDir { dir ->
        val a = spec("wrong.tflite", data)
        val bad = data.copyOf().also { it[it.size - 1] = 14 }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                Qwen3TtsDownload.downloadInto(
                    dir, client(mapOf(a.local to bad), mutableListOf()), entry,
                    listOf(a), 1_000_000_000L,
                )
            }
        }
        assertFalse(File(dir, a.local).exists())
        assertFalse(entry.verifyFiles(dir, listOf(a)))
    }

    @Test fun storagePreflightFailsBeforeAnyNetworkRequest() = withDir { dir ->
        val a = spec("large.tflite", data)
        val requests = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                Qwen3TtsDownload.downloadInto(
                    dir, client(mapOf(a.local to data), requests), entry,
                    listOf(a), 1000L,
                )
            }
        }
        assertTrue(requests.isEmpty())
    }
}
