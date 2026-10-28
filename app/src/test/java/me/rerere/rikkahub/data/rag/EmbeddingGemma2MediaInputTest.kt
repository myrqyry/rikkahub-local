package me.rerere.rikkahub.data.rag

import org.junit.Assert.*
import org.junit.Test

class EmbeddingGemma2MediaInputTest {
    @Test fun jpegAndPngBytesBecomeNativeImageInputs() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val png = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        assertNotNull(EmbeddingGemma2MediaInput.image(jpeg))
        assertNotNull(EmbeddingGemma2MediaInput.image(png))
    }

    @Test fun invalidImageBytesOrHugeInputsFailWithoutCallingNativeRuntime() {
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingGemma2MediaInput.image("not an image".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingGemma2MediaInput.image(ByteArray(32 * 1024 * 1024 + 1))
        }
    }

    @Test fun riffWavWithWaveHeaderUsesNativeAudioInput() {
        val header = "RIFF".toByteArray() + byteArrayOf(36, 0, 0, 0) + "WAVEfmt ".toByteArray()
        assertNotNull(EmbeddingGemma2MediaInput.audio(header))
    }

    @Test fun mp3AndRandomBytesAreNotMislabelledAsVerifiedAudio() {
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingGemma2MediaInput.audio(byteArrayOf(0, 1, 2, 3, 4, 5))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingGemma2MediaInput.audio("ID3".toByteArray())
        }
    }

    @Test fun textOnlyVariantRejectsMediaWithoutARealEncoder() {
        assertFalse(EmbeddingGemma2MediaInput.accepts(EmbeddingGemma2Variant.TEXT, MediaEmbeddingKind.IMAGE))
        assertFalse(EmbeddingGemma2MediaInput.accepts(EmbeddingGemma2Variant.TEXT, MediaEmbeddingKind.AUDIO))
        assertTrue(EmbeddingGemma2MediaInput.accepts(EmbeddingGemma2Variant.VISION, MediaEmbeddingKind.IMAGE))
        assertFalse(EmbeddingGemma2MediaInput.accepts(EmbeddingGemma2Variant.VISION, MediaEmbeddingKind.AUDIO))
        assertTrue(EmbeddingGemma2MediaInput.accepts(EmbeddingGemma2Variant.FULL, MediaEmbeddingKind.AUDIO))
    }
}
