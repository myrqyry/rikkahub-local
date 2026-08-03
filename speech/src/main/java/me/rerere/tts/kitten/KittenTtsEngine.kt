package me.rerere.tts.kitten

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Kitten TTS Nano synthesis engine.
 *
 * Inputs (ONNX):
 *   input_ids [1, seq_len] INT64 — token ids (BOS=0, EOS=0, vocab=175)
 *   style     [1, 256]     FLOAT — speaker embedding from voices.npz
 *   speed     [1]          FLOAT — rate multiplier (0.25..4.0, 1.0 default, inverse scale)
 *
 * Output:
 *   waveform  [num_samples] FLOAT — mono Float32 PCM at 24 kHz
 *
 * No KV-cache, no recurrence: one-shot inference per utterance.
 * Vocab is the phoneme/grapheme dictionary from [KittenTtsTokenizer].
 */
class KittenTtsEngine private constructor(
    private val bundle: KittenTtsBundle,
    private val tokenizer: KittenTtsTokenizer,
    private val config: KittenTtsConfig,
) {
    data class Outcome(val numSamples: Int, val durationFrames: Int)

    private val env: OrtEnvironment = bundle.environment

    fun synthesize(
        text: String,
        voiceOverride: String? = null,
        speedOverride: Float? = null,
    ): OutcomeAndAudio {
        require(text.isNotBlank()) { "Text must not be blank" }
        val ids = tokenizer.encode(text)
        require(ids.isNotEmpty()) { "Tokenized text is empty after filtering" }

        val voiceName = voiceOverride ?: config.voice
        val styleVec: FloatArray = bundle.voices[voiceName]
            ?: throw IllegalArgumentException("Unknown voice: $voiceName. Available: ${bundle.availableVoices.joinToString()}")

        val speed = speedOverride ?: config.speed
        require(speed.isFinite() && speed in 0.25f..4.0f) { "speed must be finite in [0.25,4.0]" }

        val waveform = runInference(ids, styleVec, speed)
        // duration output not needed for playback; could be used for telemetry
        return OutcomeAndAudio(Outcome(waveform.size, -1), waveform)
    }

    /** Direct low-level inference with pre-tokenized [inputIds] and style vector. */
    fun runInference(
        inputIds: List<Int>,
        style: FloatArray,
        speed: Float,
    ): FloatArray {
        require(style.size == 256) { "Style vector must be 256-d, got ${style.size}" }

        val inputIdsArray = inputIds.map { it.toLong() }.toLongArray()
        val speedArray = floatArrayOf(speed)

        // Conditions verified against HF artifact via onnx 1.14.1:
        //   input_ids int64 [1, sequence_length], style float [1,256], speed float [1]
        //   → waveform float [num_samples]
        val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIdsArray), longArrayOf(1, inputIdsArray.size.toLong()))
        val styleTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(style), longArrayOf(1, 256))
        val speedTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(speedArray), longArrayOf(1))

        return try {
            val inputs: Map<String, OnnxTensor> = mapOf(
                "input_ids" to inputIdsTensor,
                "style" to styleTensor,
                "speed" to speedTensor,
            )
            bundle.session().run(inputs).use { result ->
                val outTensor = result.get(0) as OnnxTensor
                val buf = outTensor.floatBuffer
                val out = FloatArray(buf.remaining())
                buf.get(out)
                out
            }
        } finally {
            inputIdsTensor.close()
            styleTensor.close()
            speedTensor.close()
        }
    }

    data class OutcomeAndAudio(val outcome: Outcome, val waveform: FloatArray)

    companion object {
        const val SAMPLE_RATE = 24000

        fun create(
            bundle: KittenTtsBundle,
            config: KittenTtsConfig = KittenTtsConfig(),
        ): KittenTtsEngine {
            val tokenizer = KittenTtsTokenizer()
            return KittenTtsEngine(bundle, tokenizer, config)
        }
    }
}
