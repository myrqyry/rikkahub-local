package me.rerere.tts.provider.providers

import android.content.Context
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Buffered OpenAI-compatible speech endpoint. WAV supports compatible local
 * backends such as Ollama Speech; existing OpenAI settings remain MP3 by default.
 * No automatic provider fallback or change to existing saved credentials.
 */
class OpenAITTSProvider(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) : TTSProvider<TTSProviderSetting.OpenAI> {

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.OpenAI,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        val format = when (providerSetting.responseFormat) {
            "mp3" -> AudioFormat.MP3
            "wav" -> AudioFormat.WAV
            else -> throw IllegalArgumentException("Speech format must be mp3 or wav")
        }
        require(request.text.isNotBlank()) { "Speech input cannot be empty" }
        val url = (providerSetting.baseUrl.trim().trimEnd('/') + "/audio/speech").toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid speech server URL")
        val body = buildJsonObject {
            put("model", JsonPrimitive(providerSetting.model))
            put("input", JsonPrimitive(request.text))
            if (providerSetting.voice.isNotBlank()) put("voice", JsonPrimitive(providerSetting.voice))
            put("response_format", JsonPrimitive(providerSetting.responseFormat))
        }
        val builder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (providerSetting.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${providerSetting.apiKey}")
        }

        // Do not log speech input, voice selections, or private credentials.
        httpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Speech request failed (HTTP ${response.code})")
            }
            val audioData = response.body.bytes()
            if (format == AudioFormat.WAV &&
                (audioData.size < 12 ||
                    !audioData.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) ||
                    !audioData.copyOfRange(8, 12).contentEquals("WAVE".toByteArray()))
            ) {
                throw IllegalStateException("Speech server returned non-WAV audio")
            }
            emit(AudioChunk(
                data = audioData,
                format = format,
                isLast = true,
                metadata = mapOf(
                    "provider" to "openai",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.voice,
                ),
            ))
        }
    }.flowOn(Dispatchers.IO)
}
