package me.rerere.locallm.litert.image.bonsai

import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BonsaiPackageDownloadTest {
    private val graph = byteArrayOf(24, 0, 0, 0) + "TFL3".toByteArray() + ByteArray(160) { (it + 1).toByte() }
    private val vocab = """{"model":"bonsai","valid":true}""".toByteArray()
    private fun digest(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun manifest() = listOf(
        BonsaiBundle.FileSpec("dit_256_int4b32.tflite", graph.size.toLong(), digest(graph)),
        BonsaiBundle.FileSpec("tokenizer/vocab.json", vocab.size.toLong(), digest(vocab)),
    )
    private val contents get() = mapOf("dit_256_int4b32.tflite" to graph, "tokenizer/vocab.json" to vocab)

    private fun client(
        overrideData: Map<String, ByteArray> = contents,
        requests: MutableList<String> = mutableListOf(),
    ): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val req = chain.request()
        val file = overrideData.keys.single { req.url.encodedPath.endsWith("/" + it) }
        requests += file + (req.header("Range")?.let { " " + it } ?: "")
        val data = overrideData.getValue(file)
        val offset = req.header("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
        val partial = data.copyOfRange(offset, data.size)
        Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(if (offset > 0) 206 else 200)
            .message("synthetic pinned file")
            .header("Content-Length", partial.size.toString())
            .apply {
                if (offset > 0) header("Content-Range", "bytes " + offset + "-" +
                    (data.size - 1) + "/" + data.size)
            }
            .body(partial.toResponseBody())
            .build()
    }.build()

    private fun withPackage(test: (File, File) -> Unit) {
        val temp = createTempDirectory("bonsai-download-test").toFile()
        val root = File(temp, "local-models").apply { mkdirs() }
        val target = File(root, "bonsai-image-ternary-4b")
        try { test(target, root) } finally { temp.deleteRecursively() }
    }

    @Test fun completeDownloadOnlyPublishesVerifiedPackage() = withPackage { target, root ->
        val ticks = mutableListOf<Long>()
        runBlocking {
            BonsaiPackageDownload.download(
                client(), target, manifest(),
                onProgress = { current, _ -> ticks += current },
                onVerifiedBeforePromote = { staging ->
                    assertFalse(target.exists())
                    assertArrayEquals(graph, File(staging, "dit_256_int4b32.tflite").readBytes())
                    File(staging, ".receipt-test").writeText("verified")
                },
            )
        }
        assertEquals("verified", File(target, ".receipt-test").readText())
        assertArrayEquals(vocab, File(target, "tokenizer/vocab.json").readBytes())
        assertEquals(graph.size.toLong() + vocab.size, ticks.last())
        assertFalse(File(root, ".bonsai-image-ternary-4b-staging").exists())
    }

    @Test fun wrongHashCannotPublishOrCorruptOtherModels() = withPackage { target, root ->
        val other = File(root, "flux2-klein").apply { mkdirs() }
        File(other, "keep.dat").writeText("untouched")
        val bad = contents.toMutableMap().apply { put("tokenizer/vocab.json", "altered".toByteArray()) }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                BonsaiPackageDownload.download(client(bad), target, manifest()) {
                    // Never called when the download fails
                    error("Should not verify a corrupt package")
                }
            }
        }
        assertFalse(target.exists())
        assertEquals("untouched", File(other, "keep.dat").readText())
    }

    @Test fun interruptedPartialResumesFromCorrectByte() = withPackage { target, root ->
        val stage = File(root, ".bonsai-image-ternary-4b-staging").apply { mkdirs() }
        val firstTarget = File(stage, "dit_256_int4b32.tflite")
        File(firstTarget.path + ".partial").writeBytes(graph.copyOfRange(0, 32))
        val requests = mutableListOf<String>()
        val client = client(requests = requests)
        runBlocking {
            BonsaiPackageDownload.download(client, target, manifest(),
                onVerifiedBeforePromote = { File(it, ".receipt-test").writeText("verified") })
        }
        assertTrue(requests.any { it == "dit_256_int4b32.tflite bytes=32-" })
        assertArrayEquals(graph, File(target, "dit_256_int4b32.tflite").readBytes())
        assertEquals("verified", File(target, ".receipt-test").readText())
    }

    @Test fun cancellationKeepsPartialForFutureResume() = withPackage { target, root ->
        // The shared downloader uses flowOn with a buffered channel. A 160-byte
        // fixture finishes before the UI collector can signal cancellation.
        // Over 64 x 64KiB chunks forces producer backpressure and tests a real
        // interrupted transfer, rather than cancellation after completion.
        val large = graph + ByteArray(8 * 1024 * 1024) { 17 }
        val oneFile = manifest().first().copy(
            bytes = large.size.toLong(), sha256 = digest(large),
        )
        assertThrows(CancellationException::class.java) {
            runBlocking {
                BonsaiPackageDownload.download(
                    client(overrideData = mapOf(oneFile.relative to large)),
                    target, listOf(oneFile),
                    onProgress = { current, _ ->
                        if (current > 0) throw CancellationException("user cancelled")
                    },
                )
            }
        }
        assertFalse(target.exists())
        val partial = File(root, ".bonsai-image-ternary-4b-staging/dit_256_int4b32.tflite.partial")
        assertTrue("Partial should survive an interrupted download", partial.exists())
        assertTrue(partial.length() in 1 until large.size.toLong())
    }

    @Test fun downloadCannotReplaceExistingPackage() = withPackage { target, _ ->
        target.mkdirs()
        File(target, "keep").writeText("existing")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { BonsaiPackageDownload.download(client(), target, manifest()) }
        }
        assertEquals("existing", File(target, "keep").readText())
    }
}
