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

    @Test fun fullPcmWavUsesNativeAudioInputAfterNormalization() {
        val input = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(40).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1.toShort()).putShort(1.toShort())
            .putInt(16000).putInt(32000).putShort(2.toShort()).putShort(16.toShort())
            .put("data".toByteArray()).putInt(4).putShort(100.toShort()).putShort((-100).toShort())
            .array()
        assertNotNull(EmbeddingGemma2MediaInput.audio(input))
    }

    @Test fun headerOnlyWavNoLongerPassesAsAudio() {
        val incomplete = "RIFF".toByteArray() + byteArrayOf(36, 0, 0, 0) + "WAVEfmt ".toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingGemma2MediaInput.audio(incomplete)
        }
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
