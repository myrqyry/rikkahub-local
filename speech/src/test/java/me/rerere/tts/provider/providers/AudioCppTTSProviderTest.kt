package me.rerere.tts.provider.providers

import android.content.ContextWrapper
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer

class AudioCppTTSProviderTest {
    private val wav = byteArrayOf(
        82, 73, 70, 70, 36, 0, 0, 0, 87, 65, 86, 69,
        102, 109, 116, 32, 16, 0, 0, 0, 1, 0, 1, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 16, 0,
        100, 97, 116, 97, 0, 0, 0, 0,
    )

    private fun client(code: Int = 200, body: ByteArray = wav, mime: String = "audio/wav", onRequest: (okhttp3.Request) -> Unit = {}): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            onRequest(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Bad Request")
                .body(body.toResponseBody(mime.toMediaType()))
                .build()
        }.build()

    @Test fun sendsAudioCppContractAndReturnsWavWithoutFakeAuth() = runBlocking {
        var requestUrl = ""
        var requestBody = ""
        var auth: String? = null
        val provider = AudioCppTTSProvider(client(onRequest = { req ->
            requestUrl = req.url.toString()
            auth = req.header("Authorization")
            requestBody = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
        }))
        val setting = TTSProviderSetting.AudioCpp(baseUrl = "http://localhost:8080/", model = "kokoro", voice = "af_heart")
        val result = provider.generateSpeech(ContextWrapper(null), setting, TTSRequest("Hello!")).single()
        assertEquals("http://localhost:8080/v1/audio/speech", requestUrl)
        assertEquals(null, auth)
        val payload = Json.parseToJsonElement(requestBody).jsonObject
        assertEquals("kokoro", payload.getValue("model").jsonPrimitive.content)
        assertEquals("Hello!", payload.getValue("input").jsonPrimitive.content)
        assertEquals("af_heart", payload.getValue("voice").jsonPrimitive.content)
        assertEquals("wav", payload.getValue("response_format").jsonPrimitive.content)
        assertArrayEquals(wav, result.data)
        assertEquals(AudioFormat.WAV, result.format)
        assertTrue(result.isLast)
        assertEquals("audio.cpp", result.metadata["provider"])
    }

    @Test fun omitsUnconfiguredVoiceAndRejectsErrorBody() {
        runBlocking {
        var body = ""
        val provider = AudioCppTTSProvider(client(onRequest = { req ->
            body = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
        }))
        provider.generateSpeech(ContextWrapper(null), TTSProviderSetting.AudioCpp(baseUrl = "http://localhost:8080", model = "kokoro", voice = ""), TTSRequest("Hello")).single()
        assertFalse(Json.parseToJsonElement(body).jsonObject.containsKey("voice"))

        val wrong = AudioCppTTSProvider(client(body = "{\"error\":\"bad model\"}".toByteArray(), mime = "application/json"))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { wrong.generateSpeech(ContextWrapper(null), TTSProviderSetting.AudioCpp(baseUrl = "http://localhost:8080", model = "kokoro"), TTSRequest("Hello")).single() }
        }
        }
    }

    @Test fun rejectsServerErrorsAndNonWavBytes() {
        val setting = TTSProviderSetting.AudioCpp(baseUrl = "http://localhost:8080", model = "kokoro")
        for ((status, bytes) in listOf(503 to wav, 200 to byteArrayOf(1, 2, 3, 4))) {
            val provider = AudioCppTTSProvider(client(code = status, body = bytes))
            assertThrows(IllegalStateException::class.java) {
                runBlocking { provider.generateSpeech(ContextWrapper(null), setting, TTSRequest("Hello")).single() }
            }
        }
    }
}
