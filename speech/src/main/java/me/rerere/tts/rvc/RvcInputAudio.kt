package me.rerere.tts.rvc

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.AudioFormat as AndroidAudioFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSResponse
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encode a bounded PCM16 WAV phrase for Voxa. Android's platform decoder is
 * used for the compressed formats produced by cloud TTS providers.
 */
object RvcInputAudio {
    private const val MAX_PCM = 20 * 1024 * 1024

    suspend fun toWav(context: Context, speech: TTSResponse): ByteArray = withContext(Dispatchers.IO) {
        when (speech.format) {
            AudioFormat.PCM -> wrapPcm(speech.audioData, speech.sampleRate ?: 24_000)
            AudioFormat.WAV -> {
                inspectWav(speech.audioData)
                speech.audioData
            }
            AudioFormat.MP3, AudioFormat.AAC, AudioFormat.OGG, AudioFormat.OPUS ->
                decodeCompressed(context, speech.audioData)
        }
    }

    fun wrapPcm(pcm: ByteArray, sampleRate: Int, channels: Int = 1): ByteArray {
        require(pcm.isNotEmpty() && pcm.size <= MAX_PCM && pcm.size % (2 * channels) == 0) {
            "RVC needs non-empty, frame-aligned PCM16 audio."
        }
        require(sampleRate in 8_000..96_000 && channels in 1..2) {
            "RVC PCM sample rate or channels are unsupported."
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size + 36)
        header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * 2)
        header.putShort((channels * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        return header.array() + pcm
    }

    /** Does not assume WAV fmt/data chunks are fixed at 44 bytes. */
    fun inspectWav(data: ByteArray) {
        require(data.size in 44..MAX_PCM && data.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
            data.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())) {
            "Voxa audio must be a bounded RIFF/WAVE file."
        }
        val riffSize = intAt(data, 4)
        require(riffSize >= 36 && riffSize.toLong() + 8L <= data.size) { "Truncated RIFF/WAVE file." }
        var offset = 12
        var formatValid = false
        var dataValid = false
        while (offset + 8 <= data.size) {
            val kind = String(data, offset, 4, Charsets.US_ASCII)
            val n = intAt(data, offset + 4)
            require(n >= 0 && offset.toLong() + 8 + n <= data.size) { "Truncated WAV chunk." }
            if (kind == "fmt ") {
                require(n >= 16) { "Incomplete WAV format block." }
                val code = shortAt(data, offset + 8)
                val channels = shortAt(data, offset + 10)
                val rate = intAt(data, offset + 12)
                val depth = shortAt(data, offset + 22)
                formatValid = code == 1 && channels in 1..2 && rate in 8_000..96_000 && depth == 16
            }
            if (kind == "data" && n >= 2) dataValid = true
            offset += 8 + n + (n and 1)
        }
        require(formatValid && dataValid) { "Voxa audio must contain uncompressed PCM16 and audio frames." }
    }

    private fun intAt(bytes: ByteArray, i: Int): Int =
        (bytes[i].toInt() and 255) or ((bytes[i+1].toInt() and 255) shl 8) or
            ((bytes[i+2].toInt() and 255) shl 16) or ((bytes[i+3].toInt() and 255) shl 24)

    private fun shortAt(bytes: ByteArray, i: Int): Int =
        (bytes[i].toInt() and 255) or ((bytes[i+1].toInt() and 255) shl 8)

    private fun decodeCompressed(context: Context, bytes: ByteArray): ByteArray {
        require(bytes.isNotEmpty() && bytes.size <= MAX_PCM) { "Compressed TTS phrase exceeds limit." }
        // MediaExtractor cannot consume a byte array. The temporary file is only
        // in app-private cache and is deleted even if decode fails.
        val file = kotlin.io.path.createTempFile(context.cacheDir.toPath(), "rvc-tts-", ".audio").toFile()
        var codec: MediaCodec? = null
        val extractor = MediaExtractor()
        try {
            file.writeBytes(bytes)
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("No decodable audio track in TTS response.")
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("TTS audio MIME missing.")
            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var eosInput = false
            var eosOutput = false
            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var stalls = 0
            while (!eosOutput) {
                if (!eosInput) {
                    val inputSlot = codec.dequeueInputBuffer(10_000)
                    if (inputSlot >= 0) {
                        val input = codec.getInputBuffer(inputSlot)
                            ?: error("Audio decoder did not provide an input buffer.")
                        input.clear()
                        val length = extractor.readSampleData(input, 0)
                        if (length < 0) {
                            codec.queueInputBuffer(inputSlot, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosInput = true
                        } else {
                            codec.queueInputBuffer(inputSlot, 0, length, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputSlot = codec.dequeueOutputBuffer(info, 10_000)
                when (outputSlot) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val resultFormat = codec.outputFormat
                        sampleRate = resultFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = resultFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val pcmEncoding = if (resultFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            resultFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        else AndroidAudioFormat.ENCODING_PCM_16BIT
                        require(pcmEncoding == AndroidAudioFormat.ENCODING_PCM_16BIT) {
                            "TTS decoder produced a PCM format other than 16-bit."
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (++stalls > 100) throw IllegalStateException("TTS audio decoder stalled.")
                    }
                    else -> if (outputSlot >= 0) {
                        stalls = 0
                        if (info.size > 0) {
                            val buffer = codec.getOutputBuffer(outputSlot) ?: error("Decoder output missing.")
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            require(pcm.size() + info.size <= MAX_PCM) { "Decoded TTS phrase is too long." }
                            val block = ByteArray(info.size)
                            buffer.get(block)
                            pcm.write(block)
                        }
                        eosOutput = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputSlot, false)
                    }
                }
            }
            return wrapPcm(pcm.toByteArray(), sampleRate, channels)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            codec?.release()
            extractor.release()
            file.delete()
        }
    }
}
