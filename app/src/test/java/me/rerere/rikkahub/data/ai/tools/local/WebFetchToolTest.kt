package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStream

/**
 * Covers web_fetch's input-validation paths, all of which early-return before any network
 * call, plus the bounded response-reader helper.
 */
class WebFetchToolTest {

    private val tool: Tool = webFetchTool(OkHttpClient())

    private fun invoke(args: String): JsonObject =
        invoke(Json.parseToJsonElement(args).jsonObject)

    private fun invoke(args: JsonObject): JsonObject {
        val text = runBlocking {
            (tool.execute(args) as List<*>)
                .filterIsInstance<UIMessagePart.Text>()
                .first().text
        }
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.error() = this["error"]?.jsonPrimitive?.content

    @Test fun `missing url is rejected`() {
        assertEquals("missing_url", invoke("""{}""").error())
    }

    @Test fun `blank url is rejected`() {
        assertEquals("missing_url", invoke("""{"url":"   "}""").error())
    }

    @Test fun `non-http url is rejected`() {
        assertEquals("bad_url", invoke("""{"url":"ftp://example.com/x"}""").error())
    }

    @Test fun `file url is rejected`() {
        assertEquals("bad_url", invoke("""{"url":"file:///etc/passwd"}""").error())
    }

    @Test fun `unsupported method is rejected`() {
        assertEquals(
            "bad_method",
            invoke("""{"url":"https://example.com","method":"DELETE"}""").error(),
        )
    }

    @Test fun `method is case-insensitive and clears method validation`() {
        // A malformed URL reaches Request.Builder only after "get" has normalized to GET.
        // This avoids a real network call and remains compatible with the localhost guard.
        assertEquals(
            "bad_request",
            invoke("""{"url":"http://","method":"get"}""").error(),
        )
    }

    @Test fun `malformed url is rejected as bad_request`() {
        // Passes the http(s)-prefix check but is not a valid URL, so request construction rejects it.
        assertEquals("bad_request", invoke("""{"url":"http://"}""").error())
    }

    @Test fun `non-object headers are rejected`() {
        assertEquals(
            "bad_headers",
            invoke("""{"url":"https://example.com","headers":[]}""").error(),
        )
    }

    @Test fun `too many headers are rejected before network access`() {
        val headers = buildJsonObject {
            repeat(WEB_FETCH_HEADER_COUNT_CAP + 1) { index -> put("X-Test-$index", "value") }
        }
        val args = buildJsonObject {
            put("url", "https://example.com")
            put("headers", headers)
        }
        assertEquals("too_many_headers", invoke(args).error())
    }

    @Test fun `oversized post body is rejected using utf8 byte size`() {
        val oversized = "é".repeat(WEB_FETCH_REQUEST_BODY_CAP_BYTES / 2 + 1)
        val args = buildJsonObject {
            put("url", "https://example.com")
            put("method", "POST")
            put("body", oversized)
        }
        assertEquals("request_body_too_large", invoke(args).error())
    }

    @Test fun `post body limit counts utf8 bytes rather than characters`() {
        val exact = "é".repeat(WEB_FETCH_REQUEST_BODY_CAP_BYTES / 2)
        assertEquals(true, webFetchBodyWithinLimit(exact))
        assertEquals(false, webFetchBodyWithinLimit(exact + "é"))
    }

    @Test fun `header limit counts combined utf8 name and value bytes`() {
        val exact = buildJsonObject {
            put("X", "a".repeat(WEB_FETCH_HEADER_BYTES_CAP - 1))
        }
        val oversized = buildJsonObject {
            put("X", "a".repeat(WEB_FETCH_HEADER_BYTES_CAP))
        }
        assertEquals(WebFetchHeaderValidation.VALID, validateWebFetchHeaders(exact))
        assertEquals(WebFetchHeaderValidation.TOO_LARGE, validateWebFetchHeaders(oversized))
    }

    @Test fun `nested header values are rejected`() {
        val headers = buildJsonObject {
            put("X-Test", buildJsonObject { put("nested", "value") })
        }
        assertEquals(
            WebFetchHeaderValidation.INVALID_VALUE,
            validateWebFetchHeaders(headers),
        )
    }

    // readBounded must never buffer more than cap+1 bytes, and must flag overflow.
    @Test fun `readBounded returns all bytes under cap without truncation`() {
        val (bytes, truncated) = readBounded("abc".byteInputStream(), 8192)
        assertEquals(3, bytes.size)
        assertEquals(false, truncated)
    }

    @Test fun `readBounded at exactly cap is not truncated`() {
        val cap = 256
        val (bytes, truncated) = readBounded(ByteArray(cap).inputStream(), cap)
        assertEquals(cap, bytes.size)
        assertEquals(false, truncated)
    }

    @Test fun `readBounded over cap stops at cap plus one and flags truncated`() {
        val cap = 256
        val (bytes, truncated) = readBounded(ByteArray(cap + 100).inputStream(), cap)
        assertEquals(cap + 1, bytes.size)
        assertEquals(true, truncated)
    }

    @Test fun `readBounded cannot spin when a stream returns zero`() {
        val (bytes, truncated) = readBounded(
            ZeroThenDataInputStream("abc".encodeToByteArray()),
            8,
        )
        assertEquals("abc", bytes.decodeToString())
        assertEquals(false, truncated)
    }

    private class ZeroThenDataInputStream(
        private val data: ByteArray,
    ) : InputStream() {
        private var returnedZero = false
        private var index = 0

        override fun read(): Int =
            if (index >= data.size) -1 else data[index++].toInt() and 0xFF

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (!returnedZero) {
                returnedZero = true
                return 0
            }
            if (index >= data.size) return -1
            val count = minOf(length, data.size - index)
            System.arraycopy(data, index, buffer, offset, count)
            index += count
            return count
        }
    }
}
