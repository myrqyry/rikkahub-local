package me.rerere.tts.pocket

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Pocket TTS synthesis coordinator. Wraps the five ONNX graphs of a [PocketTtsBundle]
 * and drives the Speech-Core synthesis loop: text conditioning, flow-matching
 * autoregression over the LM cache, and Mimi decoder frame emission.
 *
 * Graph inputs beyond the first are resolved positionally from each session's declared
 * input order (Speech-Core only enforces the first input/output name of every graph),
 * so this runs against any conforming Pocket TTS ONNX export regardless of the exact
 * state/scalar tensor names.
 */
class PocketTtsEngine private constructor(
    private val bundle: PocketTtsBundle,
    private val tokenizer: PocketTtsTokenizer,
    private val config: PocketTtsConfig,
    private val voiceEmbedding: FloatArray,
    private val voiceTokens: Int,
    private val lmCacheLength: Int,
) {

    /** Streaming callback: [samples] is one 1,920-sample (80 ms) PCM frame at 24 kHz. */
    fun interface FrameConsumer {
        fun onFrame(samples: FloatArray)
    }

    data class Outcome(
        val frames: Int,
        val stoppedOnEos: Boolean,
        val seedUsed: Long,
    )

    private val env: OrtEnvironment = bundle.environment
    private val lmInputNames: List<String> = bundle.session(PocketTtsBundle.Graph.LM_MAIN).inputInfo.keys.toList()
    private val flowInputNames: List<String> = bundle.session(PocketTtsBundle.Graph.LM_FLOW).inputInfo.keys.toList()
    private val decoderInputNames: List<String> = bundle.session(PocketTtsBundle.Graph.DECODER).inputInfo.keys.toList()
    private val conditionerInput: String = bundle.session(PocketTtsBundle.Graph.TEXT_CONDITIONER).inputInfo.keys.first()

    private val lmState: MutableList<StateSlot> =
        initialState(bundle.session(PocketTtsBundle.Graph.LM_MAIN), firstStateInput = 2)
    private val decoderState: MutableList<StateSlot> =
        initialState(bundle.session(PocketTtsBundle.Graph.DECODER), firstStateInput = 1)

    /**
     * Synthesizes [text] to streamed 24 kHz mono PCM, invoking [onFrame] once per
     * 1,920-sample decoder frame. Empty text yields no frames.
     */
    fun synthesize(text: String, onFrame: FrameConsumer): Outcome {
        val ids = tokenizer.encodeIds(text)
        if (ids.isEmpty()) return Outcome(0, stoppedOnEos = false, seedUsed = 0L)

        val limit = PocketTtsSynthesizer.frameLimit(lmCacheLength, voiceTokens, ids.size, config.maxFrames)
        val seed = resolveSeed()
        val rng = java.util.Random(seed)
        val stddev = Math.sqrt(config.temperature.toDouble()).toFloat()
        val delta = 1.0f / config.flowSteps
        val buffers = PocketTtsSynthesizer.flowBuffers(config.flowSteps)
        val empty = FloatArray(0)

        val textEmbedding = runConditioner(ids)

        resetStates()
        var frames = 0
        var eosFrame = -1
        var stoppedOnEos = false
        var current = FloatArray(LATENT_DIM) { Float.NaN }
        try {
            runLm(empty, 0, voiceEmbedding, voiceTokens)
            runLm(empty, 0, textEmbedding, ids.size)

            while (frames < limit) {
                val (conditioning, eosLogit) = runLm(current, 1, empty, 0)
                if (eosFrame < 0 && eosLogit > config.eosThreshold) eosFrame = frames
                if (PocketTtsSynthesizer.shouldStopAfterEos(frames, eosFrame.takeIf { it >= 0 }, config.framesAfterEos)) {
                    stoppedOnEos = true
                    break
                }
                val noise = noiseFrame(rng, stddev, LATENT_DIM)
                val latent = runFlow(conditioning, noise, buffers, delta)
                onFrame.onFrame(runDecoder(latent))
                frames++
                current = latent
            }
        } finally {
            resetStates()
        }
        return Outcome(frames, stoppedOnEos, seed)
    }

    private fun runConditioner(ids: List<Int>): FloatArray {
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids.map(Int::toLong).toLongArray()), longArrayOf(1, ids.size.toLong())).use { tokenIds ->
            bundle.session(PocketTtsBundle.Graph.TEXT_CONDITIONER)
                .run(mapOf(conditionerInput to tokenIds)).use { result ->
                    return readFloatArray(result.get(0) as OnnxTensor)
                }
        }
    }

    /** Runs the autoregressive LM, feeding recurrent state positionally; returns copied outputs. */
    private fun runLm(sequence: FloatArray, sequenceFrames: Int, embeddings: FloatArray, embeddingFrames: Int): Pair<FloatArray, Float> {
        val session = bundle.session(PocketTtsBundle.Graph.LM_MAIN)
        val inputs = linkedMapOf<String, OnnxTensor>()
        try {
            inputs[lmInputNames[0]] = OnnxTensor.createTensor(env, FloatBuffer.wrap(sequence), longArrayOf(1, sequenceFrames.toLong(), LATENT_DIM.toLong()))
            inputs[lmInputNames[1]] = OnnxTensor.createTensor(env, FloatBuffer.wrap(embeddings), longArrayOf(1, embeddingFrames.toLong(), EMBED_DIM.toLong()))
            lmState.forEachIndexed { i, slot -> inputs[lmInputNames[2 + i]] = slot.createTensor(env) }
            session.run(inputs).use { result ->
                val conditioning = readFloatArray(result.get(0) as OnnxTensor)
                val eosLogit = readFloat(result.get(1) as OnnxTensor)
                updateStates(result, firstStateOutput = 2, state = lmState)
                return Pair(conditioning, eosLogit)
            }
        } finally {
            inputs.values.forEach(OnnxTensor::close)
        }
    }

    /** Integrates the flow ODE over [buffers] with Euler steps of size [delta]. */
    private fun runFlow(conditioning: FloatArray, noise: FloatArray, buffers: List<Pair<Float, Float>>, delta: Float): FloatArray {
        val session = bundle.session(PocketTtsBundle.Graph.LM_FLOW)
        val scalarShape = longArrayOf(1, 1)
        var latent = noise.copyOf()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(conditioning), longArrayOf(1, EMBED_DIM.toLong())).use { condTensor ->
            buffers.forEach { (s, e) ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(s)), scalarShape).use { startTensor ->
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(e)), scalarShape).use { endTensor ->
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(latent), longArrayOf(1, LATENT_DIM.toLong())).use { latentTensor ->
                            val inputs = linkedMapOf(
                                flowInputNames[0] to condTensor,
                                flowInputNames[1] to startTensor,
                                flowInputNames[2] to endTensor,
                                flowInputNames[3] to latentTensor,
                            )
                            session.run(inputs).use { result ->
                                val direction = readFloatArray(result.get(0) as OnnxTensor)
                                latent = flowEuler(latent, direction, delta)
                            }
                        }
                    }
                }
            }
        }
        return latent
    }

    private fun runDecoder(latent: FloatArray): FloatArray {
        val session = bundle.session(PocketTtsBundle.Graph.DECODER)
        val inputs = linkedMapOf<String, OnnxTensor>()
        try {
            inputs[decoderInputNames[0]] = OnnxTensor.createTensor(env, FloatBuffer.wrap(latent), longArrayOf(1, 1, LATENT_DIM.toLong()))
            decoderState.forEachIndexed { i, slot -> inputs[decoderInputNames[1 + i]] = slot.createTensor(env) }
            session.run(inputs).use { result ->
                val audio = readFloatArray(result.get(0) as OnnxTensor)
                check(audio.size == SAMPLES_PER_FRAME) {
                    "Pocket TTS decoder returned ${audio.size} samples, expected $SAMPLES_PER_FRAME"
                }
                updateStates(result, firstStateOutput = 1, state = decoderState)
                return audio
            }
        } finally {
            inputs.values.forEach(OnnxTensor::close)
        }
    }

    private fun resetStates() {
        lmState.forEach(StateSlot::reset)
        decoderState.forEach(StateSlot::reset)
    }

    private fun resolveSeed(): Long =
        if (config.seed >= 0) config.seed else java.util.concurrent.ThreadLocalRandom.current().nextLong()

    private class StateSlot(
        val type: OnnxJavaType,
        val shape: LongArray,
        val data: Any,
    ) {
        fun reset() {
            when (data) {
                is LongArray -> data.fill(0L)
                is ByteArray -> data.fill(0)
                is FloatArray -> data.fill(0f)
            }
        }

        fun createTensor(environment: OrtEnvironment): OnnxTensor = when (type) {
            OnnxJavaType.INT64 -> OnnxTensor.createTensor(environment, LongBuffer.wrap(data as LongArray), shape)
            OnnxJavaType.BOOL -> OnnxTensor.createTensor(environment, ByteBuffer.wrap(data as ByteArray), shape, OnnxJavaType.BOOL)
            else -> OnnxTensor.createTensor(environment, FloatBuffer.wrap(data as FloatArray), shape)
        }
    }

    companion object {
        const val SAMPLE_RATE = 24000
        const val LATENT_DIM = 32
        const val EMBED_DIM = 1024
        const val SAMPLES_PER_FRAME = 1920

        fun expectedAudioSamples(frames: Int): Int = frames * SAMPLES_PER_FRAME

        fun requireInputs(inputs: List<String>, minimum: Int, graph: String): List<String> {
            require(inputs.size >= minimum) {
                "Pocket TTS graph '$graph' needs at least $minimum inputs, got ${inputs.size}"
            }
            return inputs
        }

        fun primaryOutput(outputs: List<String>): String =
            outputs.firstOrNull()
                ?: throw IllegalStateException("Pocket TTS graph declares no outputs")

        fun noiseFrame(rng: java.util.Random, stddev: Float, size: Int): FloatArray =
            FloatArray(size) { (rng.nextGaussian() * stddev).toFloat() }

        fun flowEuler(latent: FloatArray, flowDirection: FloatArray, delta: Float): FloatArray =
            FloatArray(latent.size) { latent[it] + flowDirection[it] * delta }

        /**
         * Opens a ready-to-synthesize engine over a validated [bundle] whose files live in
         * [directory]. The caller retains ownership of [bundle]; the engine does not close it.
         */
        fun create(directory: File, bundle: PocketTtsBundle, config: PocketTtsConfig = PocketTtsConfig()): PocketTtsEngine {
            val tokenizer = PocketTtsTokenizer(
                File(directory, "vocab.json").readText(),
                File(directory, "token_scores.json").readText(),
            )
            val voice = createVoiceEmbedding(bundle)
            bundle.release(PocketTtsBundle.Graph.ENCODER)
            val lmCache = lmCacheLengthOf(bundle.session(PocketTtsBundle.Graph.LM_MAIN))
            return PocketTtsEngine(bundle, tokenizer, config, voice.first, voice.second, lmCache)
        }

        private fun createVoiceEmbedding(bundle: PocketTtsBundle): Pair<FloatArray, Int> {
            val environment = bundle.environment
            val input = bundle.session(PocketTtsBundle.Graph.ENCODER).inputInfo.keys.first()
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(FloatArray(1)), longArrayOf(1, 1, 1)).use { audio ->
                bundle.session(PocketTtsBundle.Graph.ENCODER).run(mapOf(input to audio)).use { result ->
                    val latents = result.get(0) as OnnxTensor
                    val shape = (latents.info as TensorInfo).shape
                    require(shape.size == 3 && shape[0] == 1L && shape[2] == EMBED_DIM.toLong()) {
                        "Pocket TTS voice latents must be {1, voice_tokens, 1024}, got shape=${shape.joinToString()}"
                    }
                    val embedding = readFloatArray(latents)
                    return Pair(embedding, shape[1].toInt())
                }
            }
        }

        private fun lmCacheLengthOf(session: OrtSession): Int {
            val third = session.inputInfo.values.elementAtOrNull(2)?.info as? TensorInfo ?: return 1000
            return PocketTtsSynthesizer.lmCacheLength(third.shape)
        }

        private fun initialState(session: OrtSession, firstStateInput: Int): MutableList<StateSlot> {
            val infos = session.inputInfo.values.toList()
            val slots = mutableListOf<StateSlot>()
            for (index in firstStateInput until infos.size) {
                val info = infos[index].info as TensorInfo
                val shape = info.shape.map { dim -> if (dim < 0) 1L else dim }.toLongArray()
                val kind = when (info.type) {
                    OnnxJavaType.INT64 -> PocketTtsSynthesizer.StateKind.LONG
                    OnnxJavaType.BOOL -> PocketTtsSynthesizer.StateKind.BOOL
                    else -> PocketTtsSynthesizer.StateKind.FLOAT
                }
                slots += StateSlot(info.type, shape, PocketTtsSynthesizer.zeroState(kind, PocketTtsSynthesizer.stateSize(shape)))
            }
            return slots
        }

        private fun updateStates(result: OrtSession.Result, firstStateOutput: Int, state: MutableList<StateSlot>) {
            for (i in state.indices) {
                val tensor = result.get(firstStateOutput + i) as OnnxTensor
                state[i] = readSlot(state[i], tensor)
            }
        }

        private fun readSlot(slot: StateSlot, tensor: OnnxTensor): StateSlot = when (slot.type) {
            OnnxJavaType.INT64 -> StateSlot(slot.type, slot.shape, readLongArray(tensor))
            OnnxJavaType.BOOL -> StateSlot(slot.type, slot.shape, readByteArray(tensor))
            else -> StateSlot(slot.type, slot.shape, readFloatArray(tensor))
        }

        private fun readFloatArray(tensor: OnnxTensor): FloatArray {
            val buffer = tensor.floatBuffer
            val out = FloatArray(buffer.remaining())
            buffer.get(out)
            return out
        }

        private fun readFloat(tensor: OnnxTensor): Float = tensor.floatBuffer.get()

        private fun readLongArray(tensor: OnnxTensor): LongArray {
            val buffer = tensor.longBuffer
            val out = LongArray(buffer.remaining())
            buffer.get(out)
            return out
        }

        private fun readByteArray(tensor: OnnxTensor): ByteArray {
            val buffer = tensor.byteBuffer
            val out = ByteArray(buffer.remaining())
            buffer.get(out)
            return out
        }
    }
}
