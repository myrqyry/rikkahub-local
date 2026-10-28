package me.rerere.rikkahub.data.rag

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Validate PCM16 WAV and convert it to 16 kHz mono PCM16 WAV for EmbeddingGemma 2.
 *
 * RIFF/WAVE is a container, not a sample format. Handing arbitrary WAV bytes to
 * InputData.Audio risks using the wrong sample rate, number of channels or codec.
 * This local-only converter accepts 8–96 kHz 1/2-channel PCM16 and never
 * silently relabels compressed/float data. It uses sample averaging for
 * downsampling (simple antialiasing) and interpolation for upsampling.
 *
 * A more advanced resampler could improve high-frequency accuracy, but until
 * measured on device this is an explicit, bounded baseline rather than a
 * claim of studio-quality audio conversion.
 */
internal object EmbeddingGemma2WavNormalizer {
    private const val OUTPUT_RATE = 16_000
    private const val MAX_DURATION_SECONDS = 300
    private const val MAX_INPUT_BYTES = 64 * 1024 * 1024

    fun normalize(wav: ByteArray): ByteArray {
        require(wav.size in 44..MAX_INPUT_BYTES) { "Expected RIFF/WAVE PCM16 up to 64 MiB" }
        require(tag(wav, 0) == "RIFF" && tag(wav, 8) == "WAVE") {
            "Only RIFF/WAVE audio is supported"
        }
        val riffLength = unsignedInt(wav, 4)
        require(riffLength >= 4 && riffLength <= wav.size.toLong() - 8L) {
            "Invalid or truncated RIFF length"
        }
        val end = 8 + riffLength.toInt()
        var offset = 12
        var rate = 0
        var channels = 0
        var frameBytes = 0
        var pcmStart = -1
        var pcmBytes = -1
        var sawFmt = false

        while (offset <= end - 8) {
            val chunkType = tag(wav, offset)
            val size = unsignedInt(wav, offset + 4)
            val dataStart = offset + 8
            require(size <= end.toLong() - dataStart.toLong()) {
                "Truncated WAV chunk: $chunkType"
            }
            val dataBytes = size.toInt()
            when (chunkType) {
                "fmt " -> {
                    require(!sawFmt && dataBytes >= 16) { "Invalid WAV fmt chunk" }
                    sawFmt = true
                    require(ushort(wav, dataStart) == 1) {
                        "Only uncompressed PCM WAV is supported (not float, ADPCM, or MP3)"
                    }
                    channels = ushort(wav, dataStart + 2)
                    rate = signedInt(wav, dataStart + 4)
                    val byteRate = unsignedInt(wav, dataStart + 8)
                    frameBytes = ushort(wav, dataStart + 12)
                    val bitDepth = ushort(wav, dataStart + 14)
                    require(channels in 1..2) { "Only mono or stereo WAV is supported" }
                    require(rate in 8_000..96_000) { "Sample rate must be between 8 and 96 kHz" }
                    require(bitDepth == 16 && frameBytes == channels * 2 &&
                        byteRate == rate.toLong() * frameBytes) {
                        "Only correctly described 16-bit PCM WAV is supported"
                    }
                }
                "data" -> {
                    require(pcmStart < 0) { "Multiple data chunks are unsupported" }
                    pcmStart = dataStart
                    pcmBytes = dataBytes
                }
            }
            // RIFF chunks are padded to even boundaries.
            val padded = size + (size and 1L)
            require(padded <= end.toLong() - dataStart.toLong()) {
                "Truncated WAV chunk padding"
            }
            offset = (dataStart.toLong() + padded).toInt()
        }
        require(sawFmt && pcmStart >= 0 && pcmBytes > 0) {
            "WAV needs a fmt chunk and nonempty PCM data"
        }
        require(pcmBytes % frameBytes == 0) { "WAV contains a partial audio frame" }
        val frames = pcmBytes / frameBytes
        require(frames > 0 && frames.toLong() <= rate.toLong() * MAX_DURATION_SECONDS) {
            "Audio must be no longer than $MAX_DURATION_SECONDS seconds"
        }
        val outputFrames = ((frames.toLong() * OUTPUT_RATE + rate - 1L) / rate).toInt()
        val outBytes = outputFrames * 2
        val output = ByteArray(44 + outBytes)
        val header = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + outBytes)
        header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(1.toShort())
        header.putInt(OUTPUT_RATE)
        header.putInt(OUTPUT_RATE * 2)
        header.putShort(2.toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(outBytes)

        fun sample(frame: Int): Double {
            val clamped = frame.coerceIn(0, frames - 1)
            val pos = pcmStart + clamped * frameBytes
            val left = (wav[pos].toInt() and 0xff) or (wav[pos + 1].toInt() shl 8)
            if (channels == 1) return left.toShort().toDouble()
            val right = (wav[pos + 2].toInt() and 0xff) or (wav[pos + 3].toInt() shl 8)
            return (left.toShort().toDouble() + right.toShort().toDouble()) * 0.5
        }

        for (i in 0 until outputFrames) {
            val start = i.toDouble() * rate / OUTPUT_RATE
            val value = if (rate <= OUTPUT_RATE) {
                // Upsample: interpolate adjacent input frames.
                val first = floor(start).toInt()
                val fraction = start - first
                sample(first) * (1.0 - fraction) + sample(first + 1) * fraction
            } else {
                // Downsample: integrate the sample intervals to suppress aliasing.
                val stop = minOf(frames.toDouble(), (i + 1.0) * rate / OUTPUT_RATE)
                val begin = minOf(start, stop)
                var sum = 0.0
                var cursor = begin
                while (cursor < stop) {
                    val frame = floor(cursor).toInt()
                    val next = minOf(stop, frame + 1.0)
                    val weight = next - cursor
                    sum += sample(frame) * weight
                    cursor = next
                }
                if (stop > begin) sum / (stop - begin) else sample(frames - 1)
            }
            val short = value.roundToInt().coerceIn(-32768, 32767)
            val position = 44 + i * 2
            output[position] = short.toByte()
            output[position + 1] = (short ushr 8).toByte()
        }
        return output
    }

    private fun tag(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, Charsets.US_ASCII)

    private fun ushort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun signedInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            (bytes[offset + 3].toInt() shl 24)

    private fun unsignedInt(bytes: ByteArray, offset: Int): Long =
        signedInt(bytes, offset).toLong() and 0xffffffffL
}
