package me.rerere.rikkahub.data.opencode

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeHttpClientTest {
    @Test
    fun malformedHealthResponseIsRejected() = runBlocking {
        val client = clientReturning(200, "not-json")

        var failed = false
        try {
            OpenCodeHttpClient(client, "http://opencode.test", null).health()
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun httpErrorsExposeStatusAndBody() = runBlocking {
        val client = clientReturning(503, "server unavailable")

        try {
            OpenCodeHttpClient(client, "http://opencode.test", null).health()
            error("expected HTTP failure")
        } catch (error: IOException) {
            assertEquals("OpenCode HTTP 503: server unavailable", error.message)
        }
    }

    @Test
    fun projectsAndMessagesMapStablePayloads() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = when {
                chain.request().url.encodedPath == "/project" -> "[{\"id\":\"p1\",\"worktree\":\"/repo\",\"name\":\"Repo\"}]"
                else -> "[{\"id\":\"m1\",\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"text\":\"hello\"}]}]"
            }
            response(chain, 200, body)
        }.build()
        val api = OpenCodeHttpClient(client, "http://opencode.test", null)

        assertEquals(OpenCodeProject("p1", "/repo", "Repo"), api.listProjects().single())
        assertEquals(OpenCodeMessage("m1", "assistant", "hello"), api.getMessages("s1", "/repo").single())
    }

    @Test
    fun messagesReadRoleFromNestedInfo() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            response(chain, 200, "[{\"id\":\"m1\",\"info\":{\"role\":\"user\"},\"parts\":[{\"type\":\"text\",\"text\":\"hello\"}]}]")
        }.build()

        assertEquals(OpenCodeMessage("m1", "user", "hello"), OpenCodeHttpClient(client, "http://opencode.test", null).getMessages("s1", "/repo").single())
    }

    @Test
    fun messagesReadCanonicalInfoAndOnlyVisibleTextParts() = runBlocking {
        // Shape observed from the running OpenCode compatibility server's /session/{id}/message API.
        val payload = """
            [
              {
                "info": {"id": "message-1", "sessionID": "session-1", "role": "assistant"},
                "parts": [
                  {"id": "reason-1", "type": "reasoning", "text": "not answer text"},
                  {"id": "part-1", "type": "text", "text": "Hello "},
                  {"id": "tool-1", "type": "tool", "state": {"status": "completed"}},
                  {"id": "part-2", "type": "text", "text": "there"}
                ]
              },
              {"info": {"id": "message-2", "role": "user"}, "parts": [{"type": "text", "text": "Hi"}]},
              {"info": {"id": "message-3", "role": "assistant"}, "parts": [{"type": "tool", "state": {"status": "completed"}}]}
            ]
        """.trimIndent()
        val api = OpenCodeHttpClient(clientReturning(200, payload), "http://opencode.test", null)

        assertEquals(
            listOf(
                OpenCodeMessage("message-1", "assistant", "Hello there"),
                OpenCodeMessage("message-2", "user", "Hi"),
                OpenCodeMessage("message-3", "assistant", ""),
            ),
            api.getMessages("session-1", "/work"),
        )
    }

    @Test
    fun promptReadsCanonicalNestedResponse() = runBlocking {
        val client = clientReturning(
            200,
            """{"info":{"id":"reply-1","role":"assistant"},"parts":[{"type":"text","text":"Done"}]}""",
        )
        val api = OpenCodeHttpClient(client, "http://opencode.test", null)

        assertEquals(OpenCodeMessage("reply-1", "assistant", "Done"), api.prompt("session-1", "/work", "Go"))
    }

    @Test
    fun nativeV2ServerOnLegacy404GivesActionableErrorWithoutReplayingPrompt() = runBlocking {
        val paths = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            paths += path
            when (path) {
                "/api/info" -> response(chain, 200, """{"urls":["http://local"],"ready":true}""")
                else -> response(chain, 404, "not found")
            }
        }.build()
        try {
            OpenCodeHttpClient(client, "http://opencode.test", null)
                .prompt("s1", "/work", "No replay")
            error("expected version guard")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("OpenCode 2 server detected"))
        }
        assertEquals(listOf("/session/s1/message", "/api/info"), paths)
    }

    @Test
    fun htmlFallbackOnV1IsNotMisdiagnosedAsNativeV2() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            when (chain.request().url.encodedPath) {
                "/api/info" -> response(chain, 200, "<html>V1 App</html>", "text/html")
                else -> response(chain, 404, "missing")
            }
        }.build()
        try {
            OpenCodeHttpClient(client, "http://opencode.test", null).getSession("missing", null)
            error("expected V1 missing-session error")
        } catch (error: IOException) {
            assertEquals("OpenCode HTTP 404: missing", error.message)
        }
    }

    private fun clientReturning(code: Int, body: String): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain -> response(chain, code, body) }).build()

    private fun response(chain: Interceptor.Chain, code: Int, body: String, mime: String = "application/json"): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code < 300) "OK" else "Error")
            .body(body.toResponseBody(mime.toMediaType()))
            .build()
}
