package me.rerere.rikkahub.data.ai

import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Base64
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class ComfyUiSocketTest {
    private fun event(type: String, data: String): ComfyUiSocketEvent? =
        parseComfySocketEvent("""{"type":"$type","data":$data}""")

    @Test fun startedAndSamplingCarryPromptIdAndNodeStep() {
        assertEquals(ComfyUiSocketEvent.Started("job-a"),
            event("execution_start", """{"prompt_id":"job-a"}"""))
        assertEquals(
            ComfyUiSocketEvent.Sampling("job-a", "5", 7, 20),
            event("progress",
                """{"prompt_id":"job-a","node":"5","value":7,"max":20}"""),
        )
        assertEquals(ComfyUiSocketEvent.Node("job-a", "5"),
            event("executing", """{"prompt_id":"job-a","node":"5"}"""))
        assertEquals(ComfyUiSocketEvent.Node("job-a", null),
            event("executing", """{"prompt_id":"job-a","node":null}"""))
        assertEquals(ComfyUiSocketEvent.Complete("job-a"),
            event("execution_success", """{"prompt_id":"job-a","timestamp":123}"""))
    }

    @Test fun errorsAndInterruptionAreNotSuccessfulCompletions() {
        assertEquals(
            ComfyUiSocketEvent.Failed("job-b", "The VAE failed"),
            event("execution_error",
                """{"prompt_id":"job-b","exception_message":"The VAE failed"}"""),
        )
        assertEquals(
            ComfyUiSocketEvent.Failed("job-b", "execution_interrupted"),
            event("execution_interrupted",
                """{"prompt_id":"job-b","node_id":"5"}"""),
        )
    }

    @Test fun executedAndGlobalEventsMustNotImpersonateCompletion() {
        assertNull(event("executed",
            """{"prompt_id":"job-b","node":"7","output":{"images":[]}}"""))
        assertNull(event("status", """{"exec_info":{"queue_remaining":3}}"""))
        assertNull(event("progress",
            """{"prompt_id":"job-a","node":"5","value":10,"max":0}"""))
        assertNull(event("progress",
            """{"prompt_id":"job-a","node":"5","value":-1,"max":20}"""))
        assertNull(parseComfySocketEvent("not json"))
        assertNull(parseComfySocketEvent("""{"type":"execution_success","data":{}}"""))
    }

    @Test fun independentJobStatusCannotClobberCurrentOperation() {
        ComfyUiProgress.begin("job-a")
        try {
            ComfyUiProgress.update("job-b", "Sampling", 50, 100)
            assertEquals("Queued", ComfyUiProgress.status.value?.stage)
            ComfyUiProgress.update("job-a", "Sampling", 5, 20)
            assertEquals("Sampling", ComfyUiProgress.status.value?.stage)
            assertEquals(5, ComfyUiProgress.status.value?.step)
            ComfyUiProgress.clear("job-b")
            assertEquals("job-a", ComfyUiProgress.status.value?.jobId)
        } finally {
            ComfyUiProgress.clear("job-a")
        }
        assertNull(ComfyUiProgress.status.value)
    }

    @Test fun unknownNodesCannotClaimMeaningfulStages() {
        assertEquals("Running node", comfyNodeLabel("unknown"))
        assertEquals("Sampling", comfyNodeLabel("5"))
        assertEquals("Saving image", comfyNodeLabel("7"))
    }

    @Test fun realOkHttpWebSocketHandshakeAndEventDelivery() {
        // No ComfyUI or external server required. Exercise OkHttp's actual
        // WebSocket upgrade and callback path rather than only parsing JSON.
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val worker = thread(isDaemon = true) {
                server.accept().use { peer ->
                    peer.soTimeout = 5000
                    val input = peer.getInputStream().bufferedReader()
                    val lines = mutableListOf<String>()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        lines += line
                    }
                    check(lines.first().startsWith("GET /ws?clientId=client-1 "))
                    val key = lines.first { it.startsWith("Sec-WebSocket-Key:", true) }
                        .substringAfter(':').trim()
                    val hash = MessageDigest.getInstance("SHA-1").digest(
                        (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()
                    )
                    val accept = Base64.getEncoder().encodeToString(hash)
                    val out = peer.getOutputStream()
                    out.write((
                        "HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: $accept\r\n\r\n"
                    ).toByteArray())
                    out.flush()
                    val event = """{"type":"execution_start","data":{"prompt_id":"job-1"}}""".toByteArray()
                    check(event.size < 126)
                    out.write(byteArrayOf(0x81.toByte(), event.size.toByte()) + event)
                    out.flush()
                    Thread.sleep(300)
                }
            }
            runBlocking {
                val socket = ComfyUiSocket(
                    OkHttpClient(), ("http://127.0.0.1:" + server.localPort).toHttpUrl(),
                    "client-1",
                )
                try {
                    assertTrue("Expected real HTTP WebSocket upgrade", socket.connect())
                    assertEquals(
                        ComfyUiSocketEvent.Started("job-1"),
                        withTimeout(2000) { socket.events.receive() },
                    )
                } finally {
                    socket.close()
                }
            }
            worker.join(1500)
        }
    }

}
