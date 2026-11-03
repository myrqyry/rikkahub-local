package me.rerere.rikkahub.data.opencode

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.math.min

/**
 * Live read-only SSE channel. Every (re)connect is followed by a caller-owned
 * authoritative snapshot; no commands/prompts are replayed automatically.
 */
internal fun openCodeSessionEvents(
    http: OkHttpClient,
    url: String,
    authorization: String?,
    directory: String?,
    sessionId: String,
): Flow<OpenCodeSessionSignal> = callbackFlow {
    val decoder = OpenCodeSseDecoder(directory, sessionId)
    val request = Request.Builder().url(url).header("Accept", "text/event-stream").apply {
        authorization?.let { header("Authorization", it) }
    }.get().build()
    val call = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build().newCall(request)
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!call.isCanceled()) close(e)
        }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (response.code == 401 || response.code == 403) {
                    close(OpenCodeStreamAuthenticationException("OpenCode event stream requires authentication"))
                    return
                }
                if (!response.isSuccessful || !response.header("Content-Type").orEmpty().contains("text/event-stream")) {
                    close(OpenCodeStreamAuthenticationException("OpenCode server does not provide a compatible event stream (HTTP ${response.code})"))
                    return
                }
                trySend(OpenCodeSessionSignal.Connected)
                try {
                    val reader = response.body.source()
                    while (!call.isCanceled()) {
                        val line = reader.readUtf8Line() ?: break
                        val signal = decoder.accept(line.trimEnd('\r'))
                        if (signal != null) trySend(signal)
                    }
                    if (!call.isCanceled()) close(IOException("OpenCode event stream ended"))
                } catch (e: IOException) {
                    if (!call.isCanceled()) close(e)
                } catch (e: Exception) {
                    if (!call.isCanceled()) close(IOException("OpenCode event stream invalid", e))
                }
            }
        }
    })
    awaitClose { call.cancel() }
}.retryWhen { cause, attempt ->
    emit(OpenCodeSessionSignal.Disconnected(cause.message ?: "OpenCode connection lost"))
    if (cause is OpenCodeStreamAuthenticationException) return@retryWhen false
    delay(min(15000L, 1000L shl min(attempt.toInt(), 4)))
    true
}

internal class OpenCodeStreamAuthenticationException(message: String) : IOException(message)
