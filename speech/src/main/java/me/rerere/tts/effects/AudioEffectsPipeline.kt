package me.rerere.tts.effects

import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSResponse
import java.io.ByteArrayOutputStream
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.exp

/** The post-TTS stage accepts mono normalized samples; a model may change sample rate/length. */
data class PcmVoiceAudio(val samples: FloatArray, val sampleRate: Int)

fun interface VoiceAudioEffect {
    fun apply(audio: PcmVoiceAudio): PcmVoiceAudio
}

@Serializable
enum class VoiceEffectPreset { OFF, TELEPHONE }

fun VoiceEffectPreset.pipeline(): AudioEffectsPipeline = when (this) {
    VoiceEffectPreset.OFF -> AudioEffectsPipeline(emptyList())
    VoiceEffectPreset.TELEPHONE -> AudioEffectsPipeline(listOf(TelephoneBandpassEffect()))
}

class UnsupportedAudioEffectFormatException(message: String) : IllegalArgumentException(message)

/** Effects are opt-in. An empty chain deliberately preserves compressed source bytes. */
class AudioEffectsPipeline(private val effects: List<VoiceAudioEffect>) {
    fun process(response: TTSResponse): TTSResponse {
        if (effects.isEmpty()) return response
        val (pcm, rate) = when (response.format) {
            AudioFormat.PCM -> {
                val rate = requireNotNull(response.sampleRate) { "PCM sample rate required for effects" }
                require(rate in 8000..192000) { "Invalid PCM sample rate" }
                require(response.audioData.size % 2 == 0) { "Truncated PCM16 sample" }
                response.audioData to rate
            }
            AudioFormat.WAV -> Pcm16WaveCodec.fromWav(response.audioData)
            else -> throw UnsupportedAudioEffectFormatException(
                "Audio effects require mono PCM16 or WAV; ${response.format} needs decoding first"
            )
        }
        val samples = FloatArray(pcm.size / 2) { i ->
            val lo = pcm[i * 2].toInt() and 255
            val hi = pcm[i * 2 + 1].toInt() shl 8
            ((hi or lo).toShort().toFloat() / 32768f)
        }
        val output = effects.fold(PcmVoiceAudio(samples, rate)) { audio, effect ->
            effect.apply(audio)
        }
        require(output.sampleRate in 8000..192000) { "Invalid processed sample rate" }
        val processed = ByteArray(output.samples.size * 2)
        for (i in output.samples.indices) {
            require(output.samples[i].isFinite()) { "Voice effect returned non-finite audio sample at $i" }
            val bounded = output.samples[i].coerceIn(-1f, 1f)
            val value = (bounded * if (bounded < 0f) 32768f else 32767f).toInt()
            processed[i * 2] = value.toByte()
            processed[i * 2 + 1] = (value shr 8).toByte()
        }
        return response.copy(
            audioData = if (response.format == AudioFormat.WAV) Pcm16WaveCodec.toWav(processed, output.sampleRate) else processed,
            sampleRate = output.sampleRate,
        )
    }
}

/** Telephone-band coloration: high-pass around 300Hz and low-pass around 3.4kHz. */
class TelephoneBandpassEffect : VoiceAudioEffect {
    override fun apply(audio: PcmVoiceAudio): PcmVoiceAudio {
        val (samples, sampleRate) = audio
        require(sampleRate >= 8000) { "Telephone effect requires sampleRate >= 8000" }
        val hpDecay = exp(-2.0 * PI * 300.0 / sampleRate).toFloat()
        val lpDecay = exp(-2.0 * PI * 3400.0 / sampleRate).toFloat()
        var previousInput = 0f
        var highPass = 0f
        var lowPass = 0f
        var lowPassSecond = 0f
        for (i in samples.indices) {
            val current = samples[i]
            highPass = hpDecay * (highPass + current - previousInput)
            previousInput = current
            lowPass = (1f - lpDecay) * highPass + lpDecay * lowPass
            lowPassSecond = (1f - lpDecay) * lowPass + lpDecay * lowPassSecond
            samples[i] = lowPassSecond
        }
        return audio
    }
}

/** Strict single-channel, 16-bit LE PCM/WAV input contract for voice effects. */
object Pcm16WaveCodec {
    private fun fourcc(bytes: ByteArray, at: Int, value: String) =
        bytes.size >= at + 4 && bytes.copyOfRange(at, at + 4).contentEquals(value.toByteArray(Charsets.US_ASCII))

    private fun u16(data: ByteArray, pos: Int) =
        (data[pos].toInt() and 255) or ((data[pos + 1].toInt() and 255) shl 8)

    private fun u32(data: ByteArray, pos: Int): Long =
        (data[pos].toLong() and 255L) or ((data[pos + 1].toLong() and 255L) shl 8) or
            ((data[pos + 2].toLong() and 255L) shl 16) or ((data[pos + 3].toLong() and 255L) shl 24)

    fun fromWav(wav: ByteArray): Pair<ByteArray, Int> {
        require(wav.size >= 44 && fourcc(wav, 0, "RIFF") && fourcc(wav, 8, "WAVE")) { "Invalid WAV header" }
        val riffEnd = u32(wav, 4) + 8
        require(riffEnd <= wav.size && riffEnd >= 44) { "Invalid WAV RIFF length" }
        var cursor = 12
        var rate: Int? = null
        var pcm: ByteArray? = null
        while (cursor + 8 <= riffEnd) {
            val chunkSize = u32(wav, cursor + 4)
            val chunkEnd = cursor.toLong() + 8 + chunkSize
            require(chunkEnd <= riffEnd) { "Invalid WAV chunk size" }
            when {
                fourcc(wav, cursor, "fmt ") -> {
                    require(chunkSize >= 16) { "WAV fmt too small" }
                    require(u16(wav, cursor + 8) == 1 && u16(wav, cursor + 10) == 1 &&
                        u16(wav, cursor + 22) == 16) { "Effects only support mono PCM16 WAV" }
                    val hz = u32(wav, cursor + 12)
                    require(hz in 8000L..192000L) { "Invalid WAV sample rate" }
                    rate = hz.toInt()
                }
                fourcc(wav, cursor, "data") -> {
                    require(chunkSize % 2 == 0L) { "Truncated WAV PCM sample" }
                    pcm = wav.copyOfRange(cursor + 8, chunkEnd.toInt())
                }
            }
            cursor = (chunkEnd + (chunkSize and 1L)).toInt()
        }
        return requireNotNull(pcm) { "Missing WAV data" } to requireNotNull(rate) { "Missing WAV fmt" }
    }

    fun toWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        require(sampleRate in 8000..192000 && pcm.size % 2 == 0) { "Invalid mono PCM16 audio" }
        val output = ByteArrayOutputStream(pcm.size + 44)
        fun writeText(s: String) { output.write(s.toByteArray(Charsets.US_ASCII)) }
        fun writeInt(v: Int) { repeat(4) { output.write(v ushr (it * 8) and 255) } }
        fun writeShort(v: Int) { output.write(v and 255); output.write(v ushr 8 and 255) }
        writeText("RIFF"); writeInt(pcm.size + 36); writeText("WAVE")
        writeText("fmt "); writeInt(16); writeShort(1); writeShort(1)
        writeInt(sampleRate); writeInt(sampleRate * 2); writeShort(2); writeShort(16)
        writeText("data"); writeInt(pcm.size); output.write(pcm)
        return output.toByteArray()
    }
}
