package me.rerere.tts.provider.providers

import android.content.Context
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Plain audio.cpp /v1/audio/speech non-streaming WAV integration.
 *
 * The localhost default is intentionally absent: the phone needs an explicitly
 * reachable endpoint, and this provider must never silently call a cloud fallback.
 * audio.cpp returns WAV by default; reporting it as MP3 breaks playback.
 */
class AudioCppTTSProvider(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(180, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .build(),
) : TTSProvider<TTSProviderSetting.AudioCpp> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.AudioCpp,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        val base = providerSetting.baseUrl.trim().trimEnd('/')
        val url = (base + "/v1/audio/speech").toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Enter an accessible http(s) audio.cpp server URL")
        require(url.scheme == "http" || url.scheme == "https")
        require(providerSetting.model.isNotBlank()) { "Select an audio.cpp server model ID" }
        require(request.text.isNotBlank()) { "TTS text is empty" }
        val json = buildJsonObject {
            put("model", JsonPrimitive(providerSetting.model))
            put("input", JsonPrimitive(request.text))
            put("response_format", JsonPrimitive("wav"))
            if (providerSetting.voice.isNotBlank()) {
                put("voice", JsonPrimitive(providerSetting.voice))
            }
        }.toString()
        val httpRequest = Request.Builder().url(url)
            .header("Accept", "audio/wav")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("audio.cpp speech request failed (HTTP ${response.code})")
            }
            val body = response.body
            if (body.contentLength() > MAX_WAV_BYTES) {
                throw IllegalStateException("audio.cpp WAV exceeds ${MAX_WAV_BYTES / 1024 / 1024} MiB limit")
            }
            val bytes = ByteArrayOutputStream().use { output ->
                body.byteStream().use { stream ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_WAV_BYTES) {
                            throw IllegalStateException("audio.cpp WAV exceeds ${MAX_WAV_BYTES / 1024 / 1024} MiB limit")
                        }
                        output.write(buffer, 0, count)
                    }
                }
                output.toByteArray()
            }
            if (bytes.size < 12 ||
                !bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) ||
                !bytes.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())
            ) {
                throw IllegalStateException("audio.cpp response was not WAV audio")
            }
            emit(AudioChunk(
                data = bytes,
                format = AudioFormat.WAV,
                isLast = true,
                metadata = mapOf(
                    "provider" to "audio.cpp",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.voice,
                ),
            ))
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val MAX_WAV_BYTES: Int = 32 * 1024 * 1024
    }
}
