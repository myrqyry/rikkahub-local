package me.rerere.rikkahub.data.ai

import java.io.Closeable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/** One active image-page operation at a time; every update carries its job ID. */
internal data class ComfyUiRunStatus(
    val jobId: String,
    val stage: String,
    val step: Int? = null,
    val totalSteps: Int? = null,
)

internal object ComfyUiProgress {
    private val mutable = MutableStateFlow<ComfyUiRunStatus?>(null)
    val status = mutable.asStateFlow()

    fun begin(jobId: String) { mutable.value = ComfyUiRunStatus(jobId, "Queued") }

    fun update(jobId: String, stage: String, step: Int? = null, total: Int? = null) {
        if (mutable.value?.jobId == jobId) {
            mutable.value = ComfyUiRunStatus(jobId, stage, step, total)
        }
    }

    fun clear(jobId: String) {
        if (mutable.value?.jobId == jobId) mutable.value = null
    }
}

internal sealed interface ComfyUiSocketEvent {
    val promptId: String?
    data class Started(override val promptId: String) : ComfyUiSocketEvent
    data class Node(override val promptId: String, val nodeId: String?) : ComfyUiSocketEvent
    data class Sampling(
        override val promptId: String, val nodeId: String?, val step: Int, val total: Int,
    ) : ComfyUiSocketEvent
    data class Complete(override val promptId: String) : ComfyUiSocketEvent
    data class Failed(override val promptId: String, val detail: String) : ComfyUiSocketEvent
    data object Disconnected : ComfyUiSocketEvent { override val promptId: String? = null }
}

internal fun parseComfySocketEvent(text: String): ComfyUiSocketEvent? {
    val message = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    val type = message["type"]?.jsonPrimitive?.contentOrNull ?: return null
    val data = message["data"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
    val id = data["prompt_id"]?.jsonPrimitive?.contentOrNull ?: return null
    val node = data["node"]?.jsonPrimitive?.contentOrNull
    return when (type) {
        "execution_start" -> ComfyUiSocketEvent.Started(id)
        "executing" -> ComfyUiSocketEvent.Node(id, node)
        "progress" -> {
            val step = data["value"]?.jsonPrimitive?.intOrNull ?: return null
            val total = data["max"]?.jsonPrimitive?.intOrNull ?: return null
            if (total <= 0 || step < 0) return null
            ComfyUiSocketEvent.Sampling(id, node, step.coerceAtMost(total), total)
        }
        "execution_success" -> ComfyUiSocketEvent.Complete(id)
        "execution_error", "execution_interrupted" ->
            ComfyUiSocketEvent.Failed(
                id, data["exception_message"]?.jsonPrimitive?.contentOrNull
                    ?.take(250) ?: type,
            )
        else -> null // status is global; executed is only node UI output, not completion.
    }
}

/**
 * Dedicated ws connection per generation, created before submitting /prompt.
 * An HTTP history query is still required for final image metadata and for
 * recovering when the socket disconnects or misses an event.
 */
internal class ComfyUiSocket(
    private val http: OkHttpClient,
    private val endpoint: HttpUrl,
    private val clientId: String,
) : Closeable {
    val events = Channel<ComfyUiSocketEvent>(64, BufferOverflow.DROP_OLDEST)
    private val opened = CompletableDeferred<Boolean>()
    private var socket: WebSocket? = null

    suspend fun connect(): Boolean {
        // OkHttp WebSocket upgrades an HTTP(S) request; HttpUrl itself
        // accepts only http/https in Builder.scheme(). Do not rewrite
        // it to ws/wss here, or connect() fails before the handshake.
        val wsUrl = endpoint.newBuilder()
            .addPathSegment("ws")
            .addQueryParameter("clientId", clientId)
            .build()
        socket = http.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(true)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                parseComfySocketEvent(text)?.let { events.trySend(it) }
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Binary preview frames are NOT JSON and have their own header
                // format. Do not pass them as final images or invent previews.
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.complete(false)
                events.trySend(ComfyUiSocketEvent.Disconnected)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                events.trySend(ComfyUiSocketEvent.Disconnected)
            }
        })
        return withTimeoutOrNull(4500) { opened.await() } == true
    }

    override fun close() {
        socket?.close(1000, "Finished")
        events.close()
    }
}
