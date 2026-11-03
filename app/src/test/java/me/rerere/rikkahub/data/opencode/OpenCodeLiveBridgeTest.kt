package me.rerere.rikkahub.data.opencode

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeLiveBridgeTest {
    @Test fun `SSE parses multiline data and only the active session's events`() {
        val decoder = OpenCodeSseDecoder("/work", "ses_one")
        assertEquals(null, decoder.accept("event: message"))
        assertEquals(null, decoder.accept("data: {\"directory\":\"/work\","))
        assertEquals(null, decoder.accept("data: \"payload\":{\"type\":\"permission.asked\",\"properties\":{\"sessionID\":\"ses_one\"}}}"))
        assertEquals(OpenCodeSessionSignal.PermissionsChanged, decoder.accept(""))
        assertEquals(null, decoder.accept("data: {\"directory\":\"/different\",\"payload\":{\"type\":\"permission.asked\",\"properties\":{\"sessionID\":\"ses_one\"}}}"))
        assertEquals(null, decoder.accept(""))
        assertEquals(null, decoder.accept("data: {\"directory\":\"/work\",\"payload\":{\"type\":\"message.updated\",\"properties\":{\"info\":{\"sessionID\":\"ses_two\"}}}}"))
        assertEquals(null, decoder.accept(""))
        assertEquals(null, decoder.accept("data: {\"directory\":\"/work\",\"payload\":{\"type\":\"message.part.updated\",\"properties\":{\"part\":{\"sessionID\":\"ses_one\"}}}}"))
        assertEquals(OpenCodeSessionSignal.MessagesChanged, decoder.accept(""))
    }

    @Test fun `SSE accepts native envelope and ignores malformed and oversized frames`() {
        val decoder = OpenCodeSseDecoder("/work", "s1")
        assertEquals(null, decoder.accept("data: invalid json"))
        assertEquals(null, decoder.accept(""))
        assertEquals(null, decoder.accept("data: " + "X".repeat(1_100_000)))
        assertEquals(null, decoder.accept(""))
        assertEquals(null, decoder.accept("data: {\"type\":\"session.status\",\"location\":{\"directory\":\"/work\"},\"data\":{\"sessionID\":\"s1\"}}"))
        assertEquals(OpenCodeSessionSignal.StatusChanged, decoder.accept(""))
    }

    @Test fun `pending permissions are scoped by session and assistant cannot approve forever`() = runBlocking {
        val paths = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            paths.add(chain.request().url.encodedPath)
            chain.request().body?.let { body ->
                val sink = okio.Buffer(); body.writeTo(sink); bodies.add(sink.readUtf8())
            }
            val body = when (chain.request().url.encodedPath) {
                "/permission" -> """[{"id":"per_1","sessionID":"s1","permission":"bash","patterns":["git status"],"metadata":{"secret":"not exported"}},{"id":"per_other","sessionID":"s2","permission":"bash","patterns":["rm -rf"]}]"""
                "/permission/per_1/reply" -> "true"
                else -> "{}"
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = OpenCodeHttpClient(http, "http://test.local", null)
        assertEquals(listOf(OpenCodePermission("per_1", "s1", "bash", listOf("git status"))),
            api.listPendingPermissions("s1", "/work"))
        assertTrue(api.replyPermission("s1", "/work", "per_1", OpenCodePermissionReply.ONCE))
        assertEquals(listOf("/permission", "/permission", "/permission/per_1/reply"), paths)
        assertEquals("""{"reply":"once"}""", bodies.single())
        assertFalse(api.replyPermission("s1", "/work", "per_other", OpenCodePermissionReply.ONCE))
    }

    @Test fun `actual OkHttp event stream emits connected then scoped message change`() = runBlocking {
        val stream = listOf(
            ": keepalive",
            "",
            """data: {"directory":"/work","payload":{"type":"message.updated","properties":{"info":{"sessionID":"s1"}}}}""",
            "",
        ).joinToString("\n") + "\n"
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .header("Content-Type", "text/event-stream")
                .body(stream.toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        val items = kotlinx.coroutines.withTimeout(3_000L) {
            OpenCodeHttpClient(http, "http://test.local", null).observeSession("s1", "/work")
                .take(2).toList()
        }
        assertEquals(listOf(OpenCodeSessionSignal.Connected, OpenCodeSessionSignal.MessagesChanged), items)
    }

    @Test fun `unexpected stream close reconnects and restores event channel without posting any prompts`() = runBlocking {
        val urls = java.util.Collections.synchronizedList(mutableListOf<String>())
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            urls += "${chain.request().method} ${chain.request().url.encodedPath}"
            val payload = if (urls.size == 1) ": keepalive\n\n" else
                """data: {"directory":"/work","payload":{"type":"session.status","properties":{"sessionID":"s1"}}}""" + "\n\n"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").header("Content-Type", "text/event-stream")
                .body(payload.toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        val observed = kotlinx.coroutines.withTimeout(5_000L) {
            OpenCodeHttpClient(http, "http://test.local", null).observeSession("s1", "/work")
                .take(4).toList()
        }
        assertEquals(
            listOf(OpenCodeSessionSignal.Connected,
                OpenCodeSessionSignal.Disconnected("OpenCode event stream ended"),
                OpenCodeSessionSignal.Connected, OpenCodeSessionSignal.StatusChanged),
            observed,
        )
        assertEquals(listOf("GET /global/event", "GET /global/event"), urls)
    }

    @Test fun `session status is read from server rather than inferred from an event`() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body("""{"s1":{"type":"busy"},"s2":{"type":"idle"}}""".toResponseBody("application/json".toMediaType()))
                .build()
        }).build()
        assertEquals("busy", OpenCodeHttpClient(http, "http://test.local", null).getSessionStatus("s1", "/work"))
    }
}
