package me.rerere.locallm

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedModelInstallTest {
    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun modelBytes(): ByteArray = "LITERTLM".toByteArray() + ByteArray(128) { it.toByte() }

    private fun fakeClient(
        bytes: ByteArray,
        code: Int = 200,
        contentRange: String? = null,
    ) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Synthetic model fixture")
            .body(bytes.toResponseBody())
            .header("Content-Length", bytes.size.toString())
            .apply {
                if (contentRange != null) header("Content-Range", contentRange)
            }
            .build()
    }.build()

    private fun withTarget(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("verified-litert-install-").toFile()
        try {
            block(File(dir, "VibeThinker-3B-int4.litertlm"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun verifiedDownloadPromotesMatchingCompleteArtifact() = withTarget { target ->
        val bytes = modelBytes()
        val expected = VerifiedModelArtifact(bytes.size.toLong(), digest(bytes))
        val events = runBlocking {
            ModelInstall.download(fakeClient(bytes), "https://example.com/model.litertlm", target, expected)
                .toList()
        }
        assertTrue(events.last() is ModelInstall.Progress.Done)
        assertEquals(bytes.toList(), target.readBytes().toList())
        assertFalse(File(target.path + ".partial").exists())
    }

    @Test fun wrongChecksumCannotRegisterOrReplaceAnyModel() = withTarget { target ->
        val bytes = modelBytes()
        val expected = VerifiedModelArtifact(bytes.size.toLong(), "0".repeat(64))
        val events = runBlocking {
            ModelInstall.download(fakeClient(bytes), "https://example.com/model.litertlm", target, expected)
                .toList()
        }
        assertTrue(events.last() is ModelInstall.Progress.Failed)
        assertFalse(target.exists())
        assertFalse(File(target.path + ".partial").exists())
    }

    @Test fun publisherChangedFileLengthIsRejected() = withTarget { target ->
        val bytes = modelBytes()
        val expected = VerifiedModelArtifact(bytes.size.toLong() + 1, digest(bytes))
        val events = runBlocking {
            ModelInstall.download(fakeClient(bytes), "https://example.com/model.litertlm", target, expected)
                .toList()
        }
        assertTrue(events.last() is ModelInstall.Progress.Failed)
        assertFalse(target.exists())
    }

    @Test fun oldFileCannotBeOverwrittenByPinnedCatalogInstall() = withTarget { target ->
        val oldBytes = modelBytes()
        target.writeBytes(oldBytes)
        val newer = oldBytes + byteArrayOf(99)
        val expected = VerifiedModelArtifact(newer.size.toLong(), digest(newer))
        val events = runBlocking {
            ModelInstall.download(fakeClient(newer), "https://example.com/model.litertlm", target, expected)
                .toList()
        }
        assertTrue(events.last() is ModelInstall.Progress.Failed)
        assertEquals(oldBytes.toList(), target.readBytes().toList())
    }

    @Test fun mismatchedContentRangeClearsOnlyPartialAndRejectsResume() = withTarget { target ->
        val bytes = modelBytes()
        val prior = File(target.path + ".partial")
        prior.writeBytes(bytes.copyOfRange(0, 16))
        val expected = VerifiedModelArtifact(bytes.size.toLong(), digest(bytes))
        val events = runBlocking {
            ModelInstall.download(
                fakeClient(bytes.copyOfRange(16, bytes.size), code = 206,
                    contentRange = "bytes 17-${bytes.size - 1}/${bytes.size}"),
                "https://example.com/model.litertlm", target, expected
            ).toList()
        }
        assertTrue(events.last() is ModelInstall.Progress.Failed)
        assertFalse(target.exists())
        assertFalse(prior.exists())
    }

    @Test fun checksumAndRangeHelpersRejectCorruptOrNonCanonicalData() = withTarget { target ->
        val bytes = modelBytes()
        target.writeBytes(bytes)
        val correct = VerifiedModelArtifact(bytes.size.toLong(), digest(bytes))
        assertTrue(ModelInstall.verifiedArtifactMatches(target, correct))
        target.appendBytes(byteArrayOf(4))
        assertFalse(ModelInstall.verifiedArtifactMatches(target, correct))
        assertTrue(ModelInstall.contentRangeMatches("bytes 16-135/136", 16, 136))
        assertFalse(ModelInstall.contentRangeMatches("bytes 15-135/136", 16, 136))
        assertFalse(ModelInstall.contentRangeMatches("bytes 16-135/137", 16, 136))
        assertFalse(ModelInstall.contentRangeMatches("bytes */136", 16, 136))
    }
}
