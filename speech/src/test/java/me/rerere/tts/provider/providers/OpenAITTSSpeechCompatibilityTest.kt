package me.rerere.tts.provider.providers

import android.content.ContextWrapper
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class OpenAITTSSpeechCompatibilityTest {
    private val wav = "RIFF".toByteArray() + byteArrayOf(-1, -1, -1, -1) + "WAVE".toByteArray() + ByteArray(34)
    private fun client(status: Int = 200, bytes: ByteArray = wav, capture: (okhttp3.Request) -> Unit = {}): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            capture(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message(if (status == 200) "OK" else "Bad Request")
                .body(bytes.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }.build()

    @Test fun supportsOllamaWavWithNoBearerHeaderAndTrailingBaseSlash() = runBlocking {
        var url = ""
        var auth: String? = null
        var body = ""
        val provider = OpenAITTSProvider(client(capture = { req ->
            url = req.url.toString()
            auth = req.header("Authorization")
            body = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
        }))
        val setting = TTSProviderSetting.OpenAI(
            baseUrl = "http://localhost:11434/v1/", apiKey = "", model = "phonon", voice = "default", responseFormat = "wav",
        )
        val result = provider.generateSpeech(ContextWrapper(null), setting, TTSRequest("Hello from Ollama")).single()
        assertEquals("http://localhost:11434/v1/audio/speech", url)
        assertEquals(null, auth)
        val data = Json.parseToJsonElement(body).jsonObject
        assertEquals("phonon", data.getValue("model").jsonPrimitive.content)
        assertEquals("default", data.getValue("voice").jsonPrimitive.content)
        assertEquals("wav", data.getValue("response_format").jsonPrimitive.content)
        assertEquals("Hello from Ollama", data.getValue("input").jsonPrimitive.content)
        assertEquals(AudioFormat.WAV, result.format)
        assertArrayEquals(wav, result.data)
    }

    @Test fun existingOpenAISettingsStillSendMp3WithBearerAuth() = runBlocking {
        var auth: String? = null
        var body = ""
        val provider = OpenAITTSProvider(client(bytes = byteArrayOf(1, 2, 3), capture = { req ->
            auth = req.header("Authorization")
            body = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
        }))
        val setting = TTSProviderSetting.OpenAI(apiKey = "secret")
        assertEquals("mp3", setting.responseFormat)
        val result = provider.generateSpeech(ContextWrapper(null), setting, TTSRequest("hi")).single()
        assertEquals("Bearer secret", auth)
        assertEquals("mp3", Json.parseToJsonElement(body).jsonObject.getValue("response_format").jsonPrimitive.content)
        assertEquals(AudioFormat.MP3, result.format)
    }

    @Test fun unsupportedFormatAndNonWavBodyAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { OpenAITTSProvider(client()).generateSpeech(ContextWrapper(null), TTSProviderSetting.OpenAI(responseFormat = "opus"), TTSRequest("Hi")).single() }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { OpenAITTSProvider(client(bytes = "{\"error\":\"not audio\"}".toByteArray())).generateSpeech(ContextWrapper(null), TTSProviderSetting.OpenAI(responseFormat = "wav"), TTSRequest("Hi")).single() }
        }
    }

    /** Real endpoint acceptance for the shared OpenAI-compatible WAV protocol.
     * This does not establish that Ollama Phonon works on Linux.
     */
    @Test fun liveAudioCppServerAcceptsTheOpenAICompatibleProvider() {
        assumeTrue(System.getenv("RIKKAHUB_AUDIOCPP_ACCEPTANCE") == "1")
        val base = System.getenv("RIKKAHUB_AUDIOCPP_URL") ?: "http://127.0.0.1:8767"
        val setting = TTSProviderSetting.OpenAI(
            baseUrl = "$base/v1", model = "kokoro", voice = "af_heart", responseFormat = "wav",
        )
        val chunk = runBlocking {
            OpenAITTSProvider().generateSpeech(
                ContextWrapper(null), setting, TTSRequest("RikkaHub can speak through our local audio engine."),
            ).single()
        }
        assertEquals(AudioFormat.WAV, chunk.format)
        assertTrue(chunk.isLast)
        assertTrue(chunk.data.size > 10_000)
        assertArrayEquals("RIFF".toByteArray(), chunk.data.copyOfRange(0, 4))
    }

    @Test fun oldOpenAISettingsRoundTripWithMp3Default() {
        val existing: TTSProviderSetting = TTSProviderSetting.OpenAI(model = "gpt-4o-mini-tts", voice = "alloy")
        val payload = Json.encodeToString(existing)
        assertFalse(payload.contains("responseFormat"))
        val decoded = Json.decodeFromString<TTSProviderSetting>(payload) as TTSProviderSetting.OpenAI
        assertEquals("mp3", decoded.responseFormat)
        assertEquals(existing, decoded)
    }
}
