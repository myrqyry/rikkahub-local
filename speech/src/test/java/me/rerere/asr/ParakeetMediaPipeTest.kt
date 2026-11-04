package me.rerere.asr

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParakeetMediaPipeTest {
    @Test fun pinnedArtifactHasExactVerifiedIdentity() {
        assertEquals(614588752L, ParakeetMediaPipeModel.sizeBytes)
        assertEquals("66c5a74cf6c2c1d2b990901f6cece5bb53e3214dbceafa0989900a6f0eeb3504", ParakeetMediaPipeModel.sha256)
        assertTrue(ParakeetMediaPipeModel.downloadUrl.contains("/resolve/1f78e5eb6175b1b2cce35f16bcf9e2027b83e8bd/"))
    }

    @Test fun providerRoundTripsWithoutChangingOtherProviders() {
        val original: ASRProviderSetting = ASRProviderSetting.ParakeetMediaPipe(modelPath = "/models/parakeet.litertlm")
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(original, json.decodeFromString<ASRProviderSetting>(json.encodeToString(ASRProviderSetting.serializer(), original)))
        assertTrue(ASRProviderSetting.Types.contains(ASRProviderSetting.ParakeetMediaPipe::class))
        assertEquals(original.id, original.copyProvider(name = "Parakeet").id)
    }

    @Test fun partialTranscriptIsReplacedByFinalAndDuplicatesAreIgnored() {
        val accumulator = ParakeetTranscriptAccumulator()
        accumulator.add(listOf(ParakeetSegment(0, 1800, "hello wor", false)))
        assertEquals("hello wor", accumulator.text())
        val final = ParakeetSegment(0, 2000, "Hello world.", true)
        val second = ParakeetSegment(2000, 2600, "How are", false)
        accumulator.add(listOf(final, second))
        assertEquals("Hello world. How are", accumulator.text())
        accumulator.add(listOf(final))
        assertEquals("Hello world. How are", accumulator.text())
        accumulator.add(listOf(ParakeetSegment(0, 1800, "hello wor", false)))
        assertEquals("Hello world. How are", accumulator.text())
    }
}
