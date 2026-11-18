package me.rerere.tts.provider.providers

import android.content.ContextWrapper
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import me.rerere.tts.controller.TextChunker

class TikTokTTSProviderTest {
    private val mp3 = byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0x64) + ByteArray(413)
    private fun client(code: Int = 200, body: String = """{"success":true,"data":"${Base64.getEncoder().encodeToString(mp3)}"}""", inspect: (okhttp3.Request) -> Unit = {}): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            inspect(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(body.toResponseBody()).build()
        }.build()

    @Test fun catalogAndSerialization() {
        val setting = TTSProviderSetting.TikTok(voice = "en_us_stitch")
        val copy = Json.decodeFromString<TTSProviderSetting>(Json.encodeToString<TTSProviderSetting>(setting))
        assertEquals(setting, copy)
        assertTrue(TTSProviderSetting.TikTok::class in TTSProviderSetting.Types)
        assertEquals(106, TikTokVoiceCatalog.voices.size)
        assertEquals(106, TikTokVoiceCatalog.voices.map { it.id }.toSet().size)
        assertEquals(16, TikTokVoiceCatalog.voices.map { it.category }.toSet().size)
        assertEquals(15, TikTokVoiceCatalog.voices.count { it.category == "Singing" })
    }

    @Test fun cloudRequestAndMp3() = runBlocking {
        val provider = TikTokTTSProvider(client(inspect = { request ->
            assertEquals("https://ottsy.weilbyte.dev/api/generation", request.url.toString())
            assertNull(request.header("Authorization"))
            val payload = Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject
            assertEquals("Hello!", payload.getValue("text").jsonPrimitive.content)
            assertEquals("en_us_stitch", payload.getValue("voice").jsonPrimitive.content)
        }))
        val result = provider.generateSpeech(ContextWrapper(null), TTSProviderSetting.TikTok(voice="en_us_stitch"), TTSRequest("Hello!")).single()
        assertArrayEquals(mp3, result.data)
        assertEquals(AudioFormat.MP3, result.format)
        assertTrue(result.isLast)
        assertEquals("tiktok-cloud",result.metadata["provider"])
    }

    @Test fun rejectsErrorsInvalidAudioAndUnknownVoice() {
        for ((status,body) in listOf(503 to "offline", 200 to """{"success":false}""", 200 to """{"success":true,"data":"aGVsbG8="}""")) {
            assertThrows(IllegalStateException::class.java) {
                runBlocking { TikTokTTSProvider(client(status, body)).generateSpeech(ContextWrapper(null), TTSProviderSetting.TikTok(), TTSRequest("Hello!")).single() }
            }
        }
        val noNetwork = TikTokTTSProvider(client(inspect = { fail("Unexpected network request") }))
        for (setting in listOf(TTSProviderSetting.TikTok(voice="bad"), TTSProviderSetting.TikTok())) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { noNetwork.generateSpeech(ContextWrapper(null),setting,TTSRequest("🌱".repeat(100))).single() }
            }
        }
    }

    @Test fun utf8ChunksPreserveTextAndCodePoints() {
        val text = "Hello 🌱 world. ".repeat(100) + "終".repeat(100)
        val chunks = splitTikTokText(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.toByteArray(Charsets.UTF_8).size <= 280 && it.isNotEmpty() })
        val queued = TextChunker().splitForProvider(text, TTSProviderSetting.TikTok())
        assertEquals(chunks.filter { it.isNotBlank() }, queued.map { it.text })
        assertEquals(queued.indices.toList(), queued.map { it.index })
        assertTrue(chunks.none { Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first()) })
    }
}
