package me.rerere.rikkahub.data.rag

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class EmbeddingGemma2WavNormalizerTest {
    private fun wav(rate: Int = 48000, channels: Int = 2, format: Int = 1,
                    samples: ShortArray = shortArrayOf(12000, -12000, -8000, 8000, 4000, 4000),
                    addJunk: Boolean = false): ByteArray {
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { pcm.putShort(it) }
        val fmt = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(format.toShort()).putShort(channels.toShort())
            .putInt(rate).putInt(rate * channels * 2)
            .putShort((channels * 2).toShort()).putShort(16).array()
        val body = ByteArrayOutputStream()
        fun chunk(tag: String, data: ByteArray) {
            body.write(tag.toByteArray(Charsets.US_ASCII))
            body.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array())
            body.write(data)
            if (data.size % 2 != 0) body.write(0)
        }
        chunk("fmt ", fmt)
        if (addJunk) chunk("JUNK", byteArrayOf(1, 2, 3))
        chunk("data", pcm.array())
        val content = body.toByteArray()
        return ByteArrayOutputStream().also {
            it.write("RIFF".toByteArray())
            it.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(content.size + 4).array())
            it.write("WAVE".toByteArray())
            it.write(content)
        }.toByteArray()
    }

    private fun intAt(data: ByteArray, position: Int): Int =
        ByteBuffer.wrap(data, position, 4).order(ByteOrder.LITTLE_ENDIAN).int

    @Test fun stereo48kIsResampledIntoMono16kWav() {
        val output = EmbeddingGemma2WavNormalizer.normalize(wav())
        assertEquals("RIFF", String(output.copyOfRange(0, 4)))
        assertEquals("WAVE", String(output.copyOfRange(8, 12)))
        assertEquals(1, output[22].toInt())
        assertEquals(16000, intAt(output, 24))
        assertEquals(2, intAt(output, 40))
        assertEquals(46, output.size)
    }

    @Test fun already16kMonoRemainsSampleAccurate() {
        val original = wav(rate = 16000, channels = 1,
            samples = shortArrayOf(-32768, -5, 0, 10, 32767))
        val output = EmbeddingGemma2WavNormalizer.normalize(original)
        assertArrayEquals(original.copyOfRange(44, original.size),
            output.copyOfRange(44, output.size))
    }

    @Test fun oppositePhaseStereoChannelsMixToSilence() {
        val input = wav(rate = 16000, channels = 2,
            samples = shortArrayOf(15000, -15000, -22000, 22000, 7000, -7000))
        val result = EmbeddingGemma2WavNormalizer.normalize(input)
        assertEquals(6, intAt(result, 40))
        assertTrue(result.copyOfRange(44, result.size).all { it == 0.toByte() })
    }

    @Test fun handlesOddMetadataChunkAndAlignedPcmChunk() {
        val output = EmbeddingGemma2WavNormalizer.normalize(
            wav(rate = 16000, channels = 1, samples = shortArrayOf(1234, -2345), addJunk = true))
        assertEquals(48, output.size)
        assertEquals(4, intAt(output, 40))
    }

    @Test fun rejectsWrongAudioFormatInsteadOfSubmittingMisleadingBytes() {
        for (format in listOf(3, 6)) {
            assertThrows(IllegalArgumentException::class.java) {
                EmbeddingGemma2WavNormalizer.normalize(wav(format = format))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingGemma2WavNormalizer.normalize("RIFF_".toByteArray())
        }
    }

    @Test fun rejectsTruncationImpossibleRateAndChannels() {
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingGemma2WavNormalizer.normalize(wav().copyOfRange(0, 46))
        }
        val rate = wav().also { it.fill(0, 24, 28) }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingGemma2WavNormalizer.normalize(rate)
        }
        val channels = wav().also { it[22] = 8; it[23] = 0 }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingGemma2WavNormalizer.normalize(channels)
        }
    }

    @Test fun resampling8kProduces16kSamplesForOneSecond() {
        val input = wav(rate = 8000, channels = 1, samples = ShortArray(8000) { (it % 300).toShort() })
        val output = EmbeddingGemma2WavNormalizer.normalize(input)
        assertEquals(16000 * 2, intAt(output, 40))
        assertEquals(44 + 32000, output.size)
    }
}
