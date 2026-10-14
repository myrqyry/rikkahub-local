package me.rerere.tts.rvc

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSResponse
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class RvcFilterSettings(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val model: String = "",
    val transpose: Int = 0,
) {
    fun requireReady(): RvcEndpoint {
        require(enabled) { "RVC effect is off." }
        require(validModelId(model)) { "Choose a valid RVC voice model from the server." }
        require(transpose in -12..12) { "RVC pitch must be between -12 and +12 semitones." }
        return RvcEndpoint.parse(endpoint)
    }
}

fun validModelId(value: String): Boolean {
    if (value.length !in 5..255 || !value.endsWith(".pth", ignoreCase = true)) return false
    if (value.startsWith("/") || '\\' in value || ':' in value || '?' in value || '#' in value) return false
    return value.split('/').all { it.isNotBlank() && it != "." && it != ".." && '\u0000' !in it }
}

/** The user chooses a server origin, never an arbitrary HTTP path. */
data class RvcEndpoint private constructor(val origin: String) {
    val statusUrl: String get() = "$origin/api/voxa/rvc/status"
    val convertUrl: String get() = "$origin/api/voxa/rvc/convert"

    companion object {
        fun parse(value: String): RvcEndpoint {
            val uri = runCatching { URI(value.trim().trimEnd('/')) }.getOrNull()
                ?: throw IllegalArgumentException("Enter a valid Voxa server URL.")
            val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Voxa URL needs a hostname.")
            require(uri.scheme in listOf("http", "https") &&
                uri.userInfo == null && uri.rawPath.orEmpty().isEmpty() &&
                uri.rawQuery == null && uri.rawFragment == null &&
                uri.port in -1..65535) { "Enter only the Voxa server origin, e.g. http://100.x.y.z:8000." }
            if (uri.scheme == "http") {
                val privateIPv4 = host.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 }
                    ?.let { octets ->
                        octets.all { it in 0..255 } && when {
                            octets[0] == 10 || octets[0] == 127 -> true
                            octets[0] == 192 && octets[1] == 168 -> true
                            octets[0] == 172 && octets[1] in 16..31 -> true
                            octets[0] == 100 && octets[1] in 64..127 -> true
                            else -> false
                        }
                    } ?: false
                require(privateIPv4 || host == "localhost" || host == "::1" ||
                    host.endsWith(".ts.net") || host.endsWith(".local")) {
                    "Plain HTTP is allowed only for a local network or Tailscale server. Use HTTPS otherwise."
                }
            }
            return RvcEndpoint(uri.toString())
        }
    }
}

/** Cancellable network postfilter, no redirects or silent original-voice fallback. */
class VoxaRvcClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(70, TimeUnit.SECONDS)
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    suspend fun listModels(endpoint: String): List<String> {
        val url = RvcEndpoint.parse(endpoint)
        val json = JSONObject(fetch(Request.Builder().url(url.statusUrl).get().build(), 256 * 1024).toString(Charsets.UTF_8))
        require(json.optBoolean("available")) { "Voxa RVC is unavailable on this server." }
        val models = json.optJSONArray("models") ?: return emptyList()
        return (0 until minOf(models.length(), 128)).mapNotNull { models.optString(it) }
            .filter(::validModelId).distinct().sorted().take(64)
    }

    suspend fun convert(context: Context, speech: TTSResponse, settings: RvcFilterSettings): TTSResponse {
        val endpoint = settings.requireReady()
        val wav = RvcInputAudio.toWav(context, speech)
        require(wav.size <= MAX_WAV) { "The TTS phrase is too large for RVC conversion." }
        val requestBody = JSONObject().apply {
            put("audioBase64", Base64.encodeToString(wav, Base64.NO_WRAP))
            put("model", settings.model)
            put("transpose", settings.transpose)
            put("indexRatio", 0.0)
            put("protect", 0.33)
        }.toString().toRequestBody("application/json".toMediaType())
        val result = fetch(Request.Builder().url(endpoint.convertUrl).post(requestBody).build(), MAX_WAV)
        RvcInputAudio.inspectWav(result)
        return TTSResponse(result, AudioFormat.WAV, metadata = speech.metadata + ("effect" to "voxa-rvc"))
    }

    private suspend fun fetch(request: Request, maxBytes: Int): ByteArray = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(IOException("Voxa RVC connection failed: " + e.message, e))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!response.isSuccessful) throw IOException("Voxa RVC returned HTTP " + response.code)
                        val input = response.body?.byteStream() ?: throw IOException("Voxa RVC response was empty.")
                        val output = ByteArrayOutputStream()
                        input.use { stream ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = stream.read(buffer)
                                if (read < 0) break
                                if (output.size() + read > maxBytes) throw IOException("Voxa RVC audio exceeds the size limit.")
                                output.write(buffer, 0, read)
                            }
                        }
                        if (cont.isActive) cont.resume(output.toByteArray())
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }
            }
        })
    }

    companion object { const val MAX_WAV = 20 * 1024 * 1024 }
}
