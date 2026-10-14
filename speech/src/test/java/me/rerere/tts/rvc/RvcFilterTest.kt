package me.rerere.tts.rvc

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RvcFilterTest {
    @Test fun disabledByDefaultAndSettingsRoundTrip() {
        assertFalse(RvcFilterSettings().enabled)
        val configured = RvcFilterSettings(true, "http://100.85.12.14:8000", "library/some voice/model.pth", -3)
        assertEquals(configured, Json.decodeFromString<RvcFilterSettings>(Json.encodeToString(configured)))
        assertEquals("http://100.85.12.14:8000/api/voxa/rvc/convert", configured.requireReady().convertUrl)
    }

    @Test fun onlyExplicitSafeHostOriginsAndCorrectPaths() {
        assertEquals("https://foo.tail1234.ts.net/api/voxa/rvc/status",
            RvcEndpoint.parse("https://foo.tail1234.ts.net").statusUrl)
        assertEquals("http://192.168.1.4:8000/api/voxa/rvc/status",
            RvcEndpoint.parse("http://192.168.1.4:8000/").statusUrl)
        listOf(
            "http://outside.example:8000",
            "http://8.8.8.8",
            "http://100.128.1.1",
            "http://127.0.0.1:8000/path",
            "http://person@127.0.0.1:8000",
            "http://127.0.0.1:8000?x=y",
            "ftp://127.0.0.1:8000",
            "http://127.0.0.1:8000#anchor",
        ).forEach { invalid ->
            assertTrue("Rejected: $invalid", runCatching { RvcEndpoint.parse(invalid) }.isFailure)
        }
    }

    @Test fun cannotEnableWithoutARealModel() {
        assertTrue(runCatching { RvcFilterSettings(true, "http://127.0.0.1:8000").requireReady() }.isFailure)
        assertTrue(runCatching {
            RvcFilterSettings(true, "http://127.0.0.1:8000", "voice.pth", 13).requireReady()
        }.isFailure)
    }

    @Test fun arbitraryPathsCannotEscapeTheLocalModelCatalogue() {
        assertTrue(validModelId("library/Foo With Spaces/model.pth"))
        assertTrue(validModelId("voice.pth"))
        listOf("../voice.pth","/absolute.pth","foo\\bar.pth","foo/../bar.pth",
            "http://example/voice.pth","/etc/passwd","model.onnx","folder//model.pth",
        ).forEach { assertFalse(it, validModelId(it)) }
    }

    @Test fun pcmFramesHaveRealRiffHeaders() {
        val samples = byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0)
        val wav = RvcInputAudio.wrapPcm(samples, 24_000)
        assertEquals(52, wav.size)
        RvcInputAudio.inspectWav(wav)
        assertTrue(samples.contentEquals(wav.copyOfRange(44, 52)))
        assertEquals("RIFF", String(wav.copyOfRange(0, 4)))
    }

    @Test fun malformedAndTruncatedOutputCannotPlay() {
        val wav = RvcInputAudio.wrapPcm(byteArrayOf(1, 0, 2, 0), 24_000)
        assertTrue(runCatching { RvcInputAudio.inspectWav(wav.copyOfRange(0, wav.size - 2)) }.isFailure)
        assertTrue(runCatching { RvcInputAudio.inspectWav("fake mp3".toByteArray()) }.isFailure)
        assertTrue(runCatching { RvcInputAudio.wrapPcm(byteArrayOf(1, 0, 2), 24_000) }.isFailure)
        assertTrue(runCatching { RvcInputAudio.wrapPcm(byteArrayOf(1, 0), 0) }.isFailure)
    }
}
