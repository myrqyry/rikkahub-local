package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.WhisperMel
import me.rerere.asr.describeWhisperLiteRTModelError
import me.rerere.asr.resolveWhisperLiteRTModel
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "WhisperLiteRT"

/**
 * On-device Whisper ASR via LiteRT/TFLite with named signatures.
 *
 * Uses the [litert-community/whisper-base](https://huggingface.co/litert-community/whisper-base)
 * model (single `whisper_base_30s_f32.tflite` file). The model exposes two signatures:
 *
 * - **encode**: Mel spectrogram `[1,80,3000]` → encoder states `[1,1500,512]`
 * - **decode**: encoder states, token IDs `[1,N]`, KV cache `[1,1,N,128]` → logits `[1,N,51865]`
 *
 * Setup: drop `whisper_base_30s_f32.tflite` into `models/whisper-litert/` in app storage
 * (download from HuggingFace, 480 MB). The controller auto-loads via SignatureRunner.
 *
 * Tokeniser: looks for `vocab.json` (GPT-2 vocabulary, 50257 base + special tokens) next to
 * the model file. Falls back to byte-decoding of token-string-lookup.
 *
 * @param appContext  Application context for AudioRecord permissions and model loading.
 * @param provider    Configuration: modelPath, language code.
 */
class WhisperLiteRTASRController(
    private val appContext: Context,
    private val provider: ASRProviderSetting.WhisperLiteRT,
) : ASRController {

    // --- state ---
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private val modelDispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1, "WhisperLiteRTDispatcher")

    private var interpreter: Interpreter? = null
    private var tokenVocab: Map<Int, ByteArray>? = null

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    // --- ASRController ---
    override fun start(onTranscriptChange: (String) -> Unit) {
        if (_state.value.status == ASRStatus.Listening) return

        val modelFile = File(provider.modelPath)
        val modelStatus = resolveWhisperLiteRTModel(provider)
        if (provider.modelPath.isBlank() || !modelStatus.exists) {
            setError(describeWhisperLiteRTModelError(provider, modelStatus))
            return
        }
        if (modelStatus.empty) {
            setError(describeWhisperLiteRTModelError(provider, modelStatus))
            return
        }
        if (modelStatus.warning != null) {
            setError(modelStatus.warning)
            return
        }

        if (ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("Microphone permission required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        _state.value = ASRState(status = ASRStatus.Listening, isAvailable = true)
        startRecorder(modelFile)
    }

    override fun stop() {
        if (_state.value.status != ASRStatus.Listening) return
        _state.value = _state.value.copy(status = ASRStatus.Stopping)
        recorderJob?.cancel()
        releaseRecorder()
        _state.value = _state.value.copy(status = ASRStatus.Idle)
    }

    override fun dispose() {
        recorderJob?.cancel()
        releaseRecorder()
        scope.cancel()
    }

    // --- internals ---

    @SuppressLint("MissingPermission")
    private fun startRecorder(modelFile: File) {
        recorderJob?.cancel()
        recorderJob = scope.launch(modelDispatcher) {
            val vocab = loadVocab(modelFile.parentFile ?: File("."))
            tokenVocab = vocab

            val interp = runCatching {
                Interpreter(modelFile, Interpreter.Options().setNumThreads(2))
            }.getOrElse { e ->
                Log.e(TAG, "Failed to load model", e)
                setError("Failed to load whisper model: ${e.message}")
                return@launch
            }
            interpreter = interp

            val signatureKeys = runCatching { interp.getSignatureKeys() }.getOrNull()
            Log.i(TAG, "Model signatures: ${signatureKeys?.joinToString()}")
            // Warm up encoder
            runCatching {
                val dummyMel = ByteBuffer.allocateDirect(1 * 80 * 3000 * 4).order(ByteOrder.nativeOrder())
                val dummyEnc = ByteBuffer.allocateDirect(1 * 1500 * 512 * 4).order(ByteOrder.nativeOrder())
                val encInputs = arrayOf(dummyMel)
                runSignature(interp, "encode", encInputs, arrayOf(dummyEnc))
            }.onFailure { Log.w(TAG, "Encoder warm-up failed (non-fatal)", it) }

            // Audio capture
            val captureSampleRate = 16000
            val channelMask = AudioFormat.CHANNEL_IN_MONO
            val minBufSize = AudioRecord.getMinBufferSize(
                captureSampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufSize.coerceAtLeast(1600).coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                captureSampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            audioRecord = recorder

            // Ring buffer: 30 s @ 16 kHz = 480_000 samples
            val ringSize = 30 * captureSampleRate
            val pcmRing = FloatArray(ringSize)
            var filled = 0
            var samplesSinceTranscription = 0
            val transcriptionStride = 2 * captureSampleRate

            try {
                recorder.startRecording()
                val shortBuffer = ShortArray(bufferSize / 2)
                val byteBuffer = ByteArray(bufferSize)
                while (isActive) {
                    val read = recorder.read(byteBuffer, 0, byteBuffer.size)
                    if (read > 0) {
                        ByteBuffer.wrap(byteBuffer, 0, read)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(shortBuffer, 0, read / 2)
                        val count = read / 2
                        if (filled + count > ringSize) {
                            val drop = filled + count - ringSize
                            System.arraycopy(pcmRing, drop, pcmRing, 0, filled - drop)
                            filled -= drop
                        }
                        for (i in 0 until count) pcmRing[filled + i] = shortBuffer[i] / 32768f
                        filled += count

                        samplesSinceTranscription += count
                        // Whisper requires a 30-second input; pad the initial window and
                        // then keep the latest 30 seconds for interactive updates.
                        if (filled > 0 && samplesSinceTranscription >= transcriptionStride) {
                            val result = transcribe(interp, pcmRing.copyOf(), vocab)
                            if (result.isNotBlank()) {
                                _state.update { it.copy(transcript = result, errorMessage = null) }
                                onTranscriptChange?.invoke(result)
                            }
                            samplesSinceTranscription = 0
                        }
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Whisper LiteRT inference failed", e)
                setError(e.message ?: "Inference failed")
            } finally {
                releaseRecorder()
            }
        }
    }

    /** Run encode → decode loop on [pcm] (480k mono float samples, 30s @ 16kHz). */
    private fun transcribe(interp: Interpreter, pcm: FloatArray, vocab: Map<Int, ByteArray>?): String {
        val logMel = WhisperMel.compute(pcm) // [80 * 3000] = 240_000 floats
        val melBuffer = ByteBuffer.allocateDirect(logMel.size * 4).order(ByteOrder.nativeOrder())
        melBuffer.asFloatBuffer().put(logMel)

        val encOutputSize = 1 * 1500 * 512
        val encBuffer = ByteBuffer.allocateDirect(encOutputSize * 4).order(ByteOrder.nativeOrder())

        val encodeOutput = runCatching {
            val encInputs = arrayOf(melBuffer)
            runSignature(interp, "encode", encInputs, arrayOf(encBuffer))
            encBuffer.rewind()
            val encFloats = FloatArray(encOutputSize) { encBuffer.float }
            encFloats
        }.getOrElse { e ->
            Log.e(TAG, "Encoder failed", e)
            return ""
        }

        return decodeTokens(interp, encodeOutput, vocab)
    }

    // --- tokenizer ---

    private val startOfTranscript = 50257
    private val endOfText = 50256
    private val transcribeToken = 50359
    private val noTimestamps = 50363
    private val langTokens = mapOf(
        "en" to 50258, "zh" to 50259, "de" to 50260, "es" to 50261,
        "ru" to 50262, "ko" to 50263, "fr" to 50264, "ja" to 50265,
        "pt" to 50266, "tr" to 50267, "pl" to 50268, "ca" to 50269,
        "nl" to 50270, "ar" to 50271, "sv" to 50272, "it" to 50273,
        "id" to 50274, "hi" to 50275, "fi" to 50276, "vi" to 50277,
        "he" to 50278, "uk" to 50279, "el" to 50280, "ms" to 50281,
        "cs" to 50282, "ro" to 50283, "da" to 50284, "hu" to 50285,
        "ta" to 50286, "no" to 50287, "th" to 50288, "ur" to 50289,
    )

    /**
     * Auto-regressive decode loop.
     *
     * Token IDs from the GPT-2 vocabulary (50256 base) + whisper special tokens.
     * Prompt: `<|startoftranscript|> <|en|> <|transcribe|> <|notimestamps|>`
     * Max output: 128 tokens. KV cache accumulates across steps.
     */
    private fun decodeTokens(
        interp: Interpreter,
        encoderOutput: FloatArray,
        vocab: Map<Int, ByteArray>?,
    ): String {
        return runCatching {
            val maxTokens = 128
            val vocabSize = 51865
            val kvDim = 128
            val langToken = langTokens[provider.language] ?: langTokens["en"]!!
            val prompt = intArrayOf(startOfTranscript, langToken, transcribeToken, noTimestamps)
            val tokens = IntArray(maxTokens) { 0 }
            var tokenLen = prompt.size
            prompt.copyInto(tokens)

            // Pre-allocated buffers — reused every step
            val encBuf = ByteBuffer.allocateDirect(encoderOutput.size * 4)
                .order(ByteOrder.nativeOrder())
            encBuf.asFloatBuffer().put(encoderOutput)

            val outBuf = ByteBuffer.allocateDirect(maxTokens * vocabSize * 4)
                .order(ByteOrder.nativeOrder())
            val logits = FloatArray(maxTokens * vocabSize)

            // KV cache ping-pong: step N output → step N+1 input
            val kv0 = ByteBuffer.allocateDirect(1 * 1 * maxTokens * kvDim * 4)
                .order(ByteOrder.nativeOrder())
            val kv1 = ByteBuffer.allocateDirect(1 * 1 * maxTokens * kvDim * 4)
                .order(ByteOrder.nativeOrder())
            var kvIn = kv0
            var kvOut = kv1

            var resultText = ""

            for (step in 0 until (maxTokens - tokenLen)) {
                val tokenBuf = ByteBuffer.allocateDirect(tokenLen * 4)
                    .order(ByteOrder.nativeOrder())
                tokenBuf.asIntBuffer().put(tokens, 0, tokenLen)

                outBuf.rewind()
                kvIn.rewind()
                kvOut.rewind()

                val decodeInputs = arrayOf(encBuf, tokenBuf, kvIn)
                runSignature(interp, "decode", decodeInputs, arrayOf(outBuf, kvOut))

                outBuf.rewind()
                outBuf.asFloatBuffer().get(logits, 0, tokenLen * vocabSize)
                val lastOffset = (tokenLen - 1) * vocabSize
                var maxIdx = 0
                var maxVal = Float.NEGATIVE_INFINITY
                for (i in 0 until vocabSize) {
                    val v = logits[lastOffset + i]
                    if (v > maxVal) { maxVal = v; maxIdx = i }
                }

                if (maxIdx == endOfText) break
                tokens[tokenLen] = maxIdx
                tokenLen++

                // Ping-pong KV cache for next step
                val tmp = kvIn; kvIn = kvOut; kvOut = tmp

                resultText = decodeTokenSequence(tokens, tokenLen, vocab)
            }

            resultText.replace("<|startoftranscript|>", "")
                .replace("<|transcribe|>", "")
                .replace("<|notimestamps|>", "")
                .replace("<|endoftext|>", "")
                .trim()
        }.getOrElse { e ->
            Log.e(TAG, "Decode failed", e)
            ""
        }
    }

    /** Convert token IDs to text using vocab or byte-level fallback. */
    private fun decodeTokenSequence(
        tokens: IntArray, len: Int, vocab: Map<Int, ByteArray>?,
    ): String {
        val bytes = mutableListOf<Byte>()
        for (i in 0 until len) {
            val tid = tokens[i]
            if (tid in (50256..50364)) continue // skip special tokens
            val tokBytes = vocab?.get(tid)
            if (tokBytes != null) {
                tokBytes.forEach { bytes.add(it) }
            }
        }
        // GPT-2 BPE uses byte-level encoding — convert raw bytes to UTF-8
        return runCatching { String(bytes.toByteArray(), Charsets.UTF_8) }
            .getOrDefault("")
    }

    // --- vocab loading ---

    /**
     * Load GPT-2 vocabulary from `vocab.json` next to the model.
     *
     * Expects a JSON object where keys are token strings and values are token IDs.
     * The format matches HuggingFace's tokenizer.json vocab field:
     * `{"!": 0, "\"": 1, …}` — this is an inverted index. We invert it to
     * tokenId → byte representation.
     *
     * If not found, falls back to byte-level decoding (each token = single byte),
     * which covers a subset of the vocabulary (byte-tokens < 256).
     */
    private fun loadVocab(dir: File): Map<Int, ByteArray> {
        val vocabFile = File(dir, "vocab.json")
        if (!vocabFile.isFile) {
            Log.w(TAG, "vocab.json not found at ${vocabFile.absolutePath}; using byte fallback")
            return buildByteFallbackVocab()
        }
        return runCatching {
            val json = JSONObject(vocabFile.readText())
            val map = mutableMapOf<Int, ByteArray>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                val id = json.getInt(token)
                map[id] = WhisperByteLevelCodec.decodeToken(token)
            }
            Log.i(TAG, "Loaded ${map.size} vocabulary entries from vocab.json")
            map
        }.getOrElse { e ->
            Log.w(TAG, "Failed to parse vocab.json", e)
            buildByteFallbackVocab()
        }
    }

    private fun buildByteFallbackVocab(): Map<Int, ByteArray> {
        val map = mutableMapOf<Int, ByteArray>()
        // GPT-2 byte tokens: IDs 0-255 map to single byte
        for (b in 0 until 256) {
            map[b] = byteArrayOf(b.toByte())
        }
        // Extended BPE tokens: ID 256+ — common unicode sequences from GPT-2 merges
        // These can't be decoded from ID alone without the full BPE merge table.
        // The vocab.json is needed for full decoding.
        return map
    }

    private fun runSignature(
        interp: Interpreter,
        signature: String,
        inputs: Array<out Any>,
        outputs: Array<out Any>,
    ) {
        val inputNames = interp.getSignatureInputs(signature)
        val outputNames = interp.getSignatureOutputs(signature)
        require(inputNames.size == inputs.size) {
            "Whisper $signature expects ${inputNames.size} inputs, got ${inputs.size}"
        }
        require(outputNames.size == outputs.size) {
            "Whisper $signature produces ${outputNames.size} outputs, got ${outputs.size}"
        }
        interp.runSignature(
            inputNames.indices.associate { inputNames[it] to inputs[it] },
            outputNames.indices.associate { outputNames[it] to outputs[it] },
            signature,
        )
    }

    // --- helpers ---

    private fun setError(message: String) {
        _state.update {
            it.copy(status = ASRStatus.Error, errorMessage = message)
        }
    }

    @Suppress("DEPRECATION")
    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { interpreter?.close() }
        interpreter = null
    }
}
