package me.rerere.tts.matcha

import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import java.util.Random
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

class MatchaTtsEngine private constructor(
    private val bundle: MatchaTtsBundle,
    private val config: MatchaTtsConfig,
    private val textEncoder: CompiledModel,
    private val decoder: CompiledModel,
    private val vocoder: CompiledModel,
    private val g2pModel: CompiledModel,
) : AutoCloseable {
    private val preprocessor = MatchaTtsPreprocessor(bundle)
    private val g2p = MatchaTtsG2p(bundle, g2pModel)

    fun synthesize(text: String): FloatArray {
        val encoded = preprocessor.encode(g2p, text)
        val textInputs = textEncoder.createInputBuffers()
        val textOutputs = textEncoder.createOutputBuffers()
        textInputs[0].writeFloat(encoded.embeddings)
        textInputs[1].writeFloat(encoded.mask)
        textEncoder.run(textInputs, textOutputs)

        val mu = textOutputs.maxBy { it.readFloat().size }.readFloat()
        val logw = textOutputs.minBy { it.readFloat().size }.readFloat()
        val regulated = preprocessor.regulate(
            encoded,
            mu = mu,
            logw = logw,
            durationScale = config.effectiveDurationScale,
        )
        val latent = FloatArray(80 * MAX_MEL)
        val random = config.seed?.let(::Random) ?: Random()
        for (index in latent.indices) latent[index] = random.nextGaussian().toFloat()

        val decoderInputs = decoder.createInputBuffers()
        val decoderOutputs = decoder.createOutputBuffers()
        repeat(config.flowSteps) { step ->
            val t = step.toFloat() / config.flowSteps
            decoderInputs[0].writeFloat(latent)
            decoderInputs[1].writeFloat(regulated.mu)
            decoderInputs[2].writeFloat(timeEmbedding(t))
            decoderInputs[3].writeFloat(regulated.mask)
            decoder.run(decoderInputs, decoderOutputs)
            val velocity = decoderOutputs.first().readFloat()
            for (index in latent.indices) latent[index] += velocity[index] / config.flowSteps
        }

        val mel = FloatArray(latent.size) { index -> latent[index] * MEL_STD + MEL_MEAN }
        val vocoderInputs = vocoder.createInputBuffers()
        val vocoderOutputs = vocoder.createOutputBuffers()
        vocoderInputs[0].writeFloat(mel)
        vocoder.run(vocoderInputs, vocoderOutputs)
        val waveform = vocoderOutputs.first().readFloat()
        return waveform.copyOf(minOf(waveform.size, regulated.frameCount * HOP))
    }

    override fun close() {
        g2p.close()
        g2pModel.close()
        textEncoder.close()
        decoder.close()
        vocoder.close()
        bundle.close()
    }

    private fun timeEmbedding(t: Float): FloatArray {
        val result = FloatArray(160)
        for (index in 0 until 80) {
            val exponent = 1000f * t * exp(index * -ln(10000f) / 79f)
            result[index] = sin(exponent)
            result[index + 80] = cos(exponent)
        }
        return result
    }

    companion object {
        const val SAMPLE_RATE = 22050
        private const val MAX_MEL = 512
        private const val HOP = 256
        private const val MEL_MEAN = -5.536622f
        private const val MEL_STD = 2.116101f

        fun create(bundle: MatchaTtsBundle, config: MatchaTtsConfig): MatchaTtsEngine {
            return try {
                MatchaTtsEngine(
                    bundle = bundle,
                    config = config,
                    textEncoder = load(bundle.textEncoderFile, Accelerator.GPU),
                    decoder = load(bundle.decoderFile, Accelerator.CPU),
                    vocoder = load(bundle.vocoderFile, Accelerator.GPU),
                    g2pModel = load(bundle.g2pFile, Accelerator.CPU),
                )
            } catch (error: Throwable) {
                bundle.close()
                throw IllegalStateException("Unable to initialize Matcha TTS LiteRT graphs", error)
            }
        }

        private fun load(file: java.io.File, accelerator: Accelerator): CompiledModel =
            CompiledModel.create(file.absolutePath, CompiledModel.Options(accelerator), null)
    }
}
