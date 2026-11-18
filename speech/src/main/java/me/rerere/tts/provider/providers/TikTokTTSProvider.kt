package me.rerere.tts.provider.providers

import android.content.Context
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

private const val ENDPOINT = "https://ottsy.weilbyte.dev/api/generation"
private const val MAX_TEXT_BYTES = 280
private const val MAX_RESPONSE_BYTES = 6_000_000L
private val responseJson = Json { ignoreUnknownKeys = true }

/** Preserve Unicode and prefer sentence/word breaks; each piece is one playable MP3. */
internal fun splitTikTokText(text: String): List<String> {
    val segments = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = start
        var bytes = 0
        var sentence = start
        var word = start
        while (end < text.length) {
            val cp = text.codePointAt(end)
            val next = end + Character.charCount(cp)
            val size = text.substring(end, next).toByteArray(Charsets.UTF_8).size
            if (bytes + size > MAX_TEXT_BYTES) break
            bytes += size
            end = next
            if (cp.toChar() in ".!?。！？") sentence = end
            if (Character.isWhitespace(cp)) {
                word = end
                if (sentence == end - Character.charCount(cp)) sentence = end
            }
        }
        if (end < text.length) {
            val threshold = start + (end - start) / 2
            if (sentence > start && sentence >= threshold) end = sentence
            else if (word > start && word >= threshold) end = word
        }
        segments.add(text.substring(start, end))
        start = end
    }
    return segments
}

@Serializable
private data class TikTokResponse(val success: Boolean = false, val data: String? = null)

class TikTokTTSProvider(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) : TTSProvider<TTSProviderSetting.TikTok> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.TikTok,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        require(providerSetting.voice in TikTokVoiceCatalog.byId) { "Unknown TikTok voice" }
        require(request.text.isNotBlank() && request.text.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) {
            "TikTok speech requires 1–280 UTF-8 bytes per request"
        }
        val body = buildJsonObject {
            put("text", request.text)
            put("voice", providerSetting.voice)
        }.toString()
        val httpRequest = Request.Builder().url(ENDPOINT)
            .post(body.toRequestBody("application/json".toMediaType())).build()
        val audio = synthesize(httpClient.newCall(httpRequest))
        emit(AudioChunk(
            data = audio,
            format = AudioFormat.MP3,
            isLast = true,
            metadata = mapOf("provider" to "tiktok-cloud", "voice" to providerSetting.voice),
        ))
    }

    private suspend fun synthesize(call: Call): ByteArray = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        check(it.isSuccessful) { "TikTok cloud synthesis failed (HTTP ${it.code})" }
                        val source = it.body.source()
                        check(!source.request(MAX_RESPONSE_BYTES + 1)) { "TikTok response too large" }
                        val payload = responseJson.decodeFromString<TikTokResponse>(source.readUtf8())
                        check(payload.success && !payload.data.isNullOrBlank()) { "TikTok returned no audio" }
                        val bytes = try {
                            Base64.getDecoder().decode(payload.data)
                        } catch (_: IllegalArgumentException) {
                            throw IllegalStateException("TikTok returned invalid audio encoding")
                        }
                        check(bytes.size >= 100 && (
                            bytes.copyOfRange(0, 3).contentEquals("ID3".toByteArray()) ||
                            (bytes[0] == 0xff.toByte() && (bytes[1].toInt() and 0xe0) == 0xe0)
                        )) { "TikTok returned invalid MP3 audio" }
                        bytes
                    }
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
}
