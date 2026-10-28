package me.rerere.rikkahub.data.rag

import com.google.ai.edge.litertlm.InputData

enum class MediaEmbeddingKind { IMAGE, AUDIO }

/** Tight, explicit media contracts before passing untrusted bytes to native LiteRT-LM. */
object EmbeddingGemma2MediaInput {
    private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
    private const val MAX_AUDIO_BYTES = 64 * 1024 * 1024

    fun accepts(variant: EmbeddingGemma2Variant, kind: MediaEmbeddingKind): Boolean =
        when (kind) {
            MediaEmbeddingKind.IMAGE -> variant.supportsImage
            MediaEmbeddingKind.AUDIO -> variant.supportsAudio
        }

    fun image(bytes: ByteArray): InputData.Image {
        require(bytes.isNotEmpty() && bytes.size <= MAX_IMAGE_BYTES) {
            "Image must be at most 32 MiB"
        }
        val jpeg = bytes.size >= 3 && bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val png = bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        )
        require(jpeg || png) { "Only JPEG and PNG images are supported" }
        return InputData.Image(bytes)
    }

    fun audio(bytes: ByteArray): InputData.Audio {
        require(bytes.size in 16..MAX_AUDIO_BYTES) { "WAV audio must be at most 64 MiB" }
        require(bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) &&
            bytes.copyOfRange(8, 12).contentEquals("WAVE".toByteArray(Charsets.US_ASCII))) {
            "Only RIFF/WAVE audio files are supported"
        }
        return InputData.Audio(bytes)
    }
}
