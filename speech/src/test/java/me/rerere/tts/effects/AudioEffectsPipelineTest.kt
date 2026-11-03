package me.rerere.tts.effects

import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSResponse
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioEffectsPipelineTest {
    private fun pcm(frequency: Double, rate: Int = 16000, seconds: Double = 0.25): TTSResponse {
        val count = (seconds * rate).toInt()
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val sample = (sin(2 * PI * frequency * i / rate) * 10000).toInt()
            bytes[2 * i] = sample.toByte()
            bytes[2 * i + 1] = (sample shr 8).toByte()
        }
        return TTSResponse(bytes, AudioFormat.PCM, sampleRate = rate)
    }

    private fun rms(bytes: ByteArray): Double {
        var squares = 0.0
        for (i in bytes.indices step 2) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toDouble()
            squares += sample * sample
        }
        return sqrt(squares / (bytes.size / 2))
    }

    @Test fun disabledPipelineLeavesCompressedAudioUntouched() {
        val original = TTSResponse(byteArrayOf(1, 2, 3), AudioFormat.MP3)
        assertSame(original, AudioEffectsPipeline(emptyList()).process(original))
    }

    @Test fun telephoneEffectSuppressesOutOfBandFrequenciesAndKeepsSpeechBand() {
        val effect = AudioEffectsPipeline(listOf(TelephoneBandpassEffect()))
        val speech = pcm(1000.0)
        val bass = pcm(70.0)
        val treble = pcm(6800.0)
        val s = effect.process(speech)
        val b = effect.process(bass)
        val t = effect.process(treble)
        assertEquals(AudioFormat.PCM, s.format)
        assertEquals(16000, s.sampleRate)
        assertTrue("speech band should remain", rms(s.audioData) > rms(speech.audioData) * 0.35)
        assertTrue("bass should be attenuated", rms(b.audioData) < rms(bass.audioData) * 0.3)
        assertTrue("treble should be attenuated", rms(t.audioData) < rms(treble.audioData) * 0.4)
    }

    @Test fun wavIsDecodedAndEncodedWithRealSampleRate() {
        val source = pcm(1000.0)
        val wave = Pcm16WaveCodec.toWav(source.audioData, 16000)
        val processed = AudioEffectsPipeline(listOf(TelephoneBandpassEffect()))
            .process(TTSResponse(wave, AudioFormat.WAV))
        assertEquals(AudioFormat.WAV, processed.format)
        assertEquals(16000, processed.sampleRate)
        assertEquals(wave.size, processed.audioData.size)
        assertArrayEquals("RIFF".toByteArray(), processed.audioData.copyOfRange(0, 4))
        assertFalse(wave.contentEquals(processed.audioData))
    }

    @Test fun unsupportedAudioCannotSilentlyPretendToBeProcessed() {
        try {
            AudioEffectsPipeline(listOf(TelephoneBandpassEffect()))
                .process(TTSResponse(byteArrayOf(1, 2), AudioFormat.MP3))
            fail("MP3 requires an explicit decoder, not a silent no-op")
        } catch (expected: UnsupportedAudioEffectFormatException) { }
    }

    @Test fun effectsComposeInSpecifiedOrder() {
        val calls = mutableListOf<String>()
        val first = VoiceAudioEffect { clip -> calls.add("first"); clip.samples[0] = 0.4f; clip }
        val second = VoiceAudioEffect { clip -> calls.add("second"); clip.samples[0] *= 0.5f; clip }
        val response = AudioEffectsPipeline(listOf(first, second))
            .process(TTSResponse(byteArrayOf(0, 0), AudioFormat.PCM, sampleRate = 16000))
        assertEquals(listOf("first", "second"), calls)
        val sample = ((response.audioData[1].toInt() shl 8) or (response.audioData[0].toInt() and 0xff)).toShort()
        assertTrue(abs(6553 - sample.toInt()) <= 1)
    }

    @Test fun nonFiniteEffectOutputIsRejectedRatherThanRenderedAsSilentSuccess() {
        val broken = VoiceAudioEffect { clip -> clip.samples[0] = Float.NaN; clip }
        try {
            AudioEffectsPipeline(listOf(broken)).process(pcm(440.0))
            fail("An invalid model/effect sample must not be passed to playback")
        } catch (expected: IllegalArgumentException) { }
    }

    @Test fun malformedWavIsRejectedBeforeAnyEffect() {
        try {
            AudioEffectsPipeline(listOf(TelephoneBandpassEffect()))
                .process(TTSResponse("RIFFbad".toByteArray(), AudioFormat.WAV))
            fail("malformed WAV should be rejected")
        } catch (expected: IllegalArgumentException) { }
    }
}
