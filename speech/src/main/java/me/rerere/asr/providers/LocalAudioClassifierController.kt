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
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
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
import me.rerere.asr.PannsMel
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import me.rerere.locallm.task.NpuTaskInference
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "LocalAudioClassifier"

/**
 * Local on-device audio classifier (PANNs-CNN14 over the raw LiteRT Interpreter).
 *
 * Slots into the existing ASRController surface so it shows up in the speech
 * settings picker like the streaming providers: `start()` records PCM16 via
 * [AudioRecord] at 32 kHz, computes a host-side log-mel spectrogram
 * ([PannsMel], mirroring torchlibrosa for the litert-community PANNs-CNN14
 * model), and emits the top-3 AudioSet tags as the "transcript".
 *
 * The model (`cnn14_audioset_fp16.tflite`) is a link-only import via
 * Settings → Speech → Local Audio Classifier; the mel filterbank
 * (`mel_basis.bin`) must sit next to it in the same directory. Runs on NPU
 * (CompiledModel) when available, else the raw Interpreter. wav2vec2-KWS
 * (two-graph frontend+head) is not supported by this rewrite.
 */
class LocalAudioClassifierController(
    private val appContext: Context,
    private val provider: ASRProviderSetting.LocalAudioClassifier,
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    // One confined dispatcher owns the models (scaffolding rule 1); the AudioRecord
    // loop below runs inside it too so capture + classify are serialized.
    private val modelDispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1, "ModelDispatcher")
    private var npuModel: CompiledModel? = null
    private var npuInput: TensorBuffer? = null
    private var npuOutput: TensorBuffer? = null
    private var interpreter: Interpreter? = null

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (_state.value.status == ASRStatus.Listening) return

        val modelFile = File(provider.modelPath)
        if (provider.modelPath.isBlank() || !modelFile.exists()) {
            _state.value = ASRState(
                status = ASRStatus.Error,
                isAvailable = true,
                errorMessage = "Local audio classifier model not installed. Copy/import a model in Settings → Speech.",
            )
            return
        }

        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("Microphone permission is required")
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

    @SuppressLint("MissingPermission")
    private fun startRecorder(modelFile: File) {
        recorderJob?.cancel()
        recorderJob = scope.launch(modelDispatcher) {
            val melBasis = PannsMel.loadMelBasis(
                File(modelFile.parentFile ?: File("."), "mel_basis.bin")
            )
            if (melBasis == null) {
                Log.e(TAG, "mel_basis.bin not found next to the model")
                setError("mel_basis.bin not found next to the model. Import it via Settings → Speech.")
                return@launch
            }

            val interpreter = runCatching {
                Interpreter(modelFile, Interpreter.Options().setNumThreads(2))
            }.getOrElse { e ->
                Log.e(TAG, "Failed to load audio classifier model", e)
                setError("Failed to load audio classifier model: ${e.message}")
                return@launch
            }
            this@LocalAudioClassifierController.interpreter = interpreter

            val inputShape = interpreter.getInputTensor(0).shape()
            if (inputShape?.size != 4 || inputShape.lastOrNull() != PannsMel.NMEL) {
                Log.e(
                    TAG,
                    "Unsupported model input shape ${inputShape?.contentToString()}; PANNs log-mel [1,1,1001,64] expected",
                )
                setError("Unsupported model: expected a PANNs-CNN14 log-mel model (input [1,1,1001,64]).")
                return@launch
            }

            val captureSampleRate = PannsMel.SAMPLE_RATE
            val channelMask = AudioFormat.CHANNEL_IN_MONO
            val minBufferSize = AudioRecord.getMinBufferSize(
                captureSampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize
                .coerceAtLeast(captureSampleRate / 10 * 2)
                .coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                captureSampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            audioRecord = recorder

            // NPU path: open once, create buffers once, warm up once — reuse for every chunk.
            val npu = runCatching { NpuTaskInference.create(appContext, modelFile.absolutePath) }.getOrNull()
            val npuIn = npu?.createInputBuffers()?.getOrNull(0) as TensorBuffer?
            val npuOut = npu?.createOutputBuffers()?.getOrNull(0) as TensorBuffer?
            if (npu != null && npuIn != null && npuOut != null) {
                // One dummy inference right after create so the first real chunk is not the NPU compile.
                runCatching {
                    npuIn.writeFloat(FloatArray(PannsMel.INPUT_FLOATS))
                    npu.run(listOf(npuIn), listOf(npuOut))
                }
            }
            this@LocalAudioClassifierController.npuModel = npu
            this@LocalAudioClassifierController.npuInput = npuIn
            this@LocalAudioClassifierController.npuOutput = npuOut

            // Warm the raw Interpreter up once too, so the first real chunk is not a JIT.
            val outputSize = interpreter.getOutputTensor(0).numBytes().toInt() / 4
            runCatching {
                val warmIn = ByteBuffer.allocateDirect(PannsMel.INPUT_FLOATS * 4)
                    .order(ByteOrder.nativeOrder())
                warmIn.asFloatBuffer().put(FloatArray(PannsMel.INPUT_FLOATS))
                val warmOut = ByteBuffer.allocateDirect(interpreter.getOutputTensor(0).numBytes().toInt())
                    .order(ByteOrder.nativeOrder())
                interpreter.run(warmIn, warmOut)
            }

            // Ring buffer holding the last 10s of PCM16, scaled to float.
            val pcmRing = FloatArray(PannsMel.CLIP_SAMPLES)
            var filled = 0

            try {
                recorder.startRecording()
                val shortBuffer = ShortArray(bufferSize / 2)
                val byteBuffer = ByteArray(bufferSize)
                while (isActive) {
                    val read = recorder.read(byteBuffer, 0, byteBuffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(byteBuffer, read)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }
                        ByteBuffer.wrap(byteBuffer, 0, read)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(shortBuffer, 0, read / 2)
                        val count = read / 2
                        if (filled + count > PannsMel.CLIP_SAMPLES) {
                            val drop = filled + count - PannsMel.CLIP_SAMPLES
                            System.arraycopy(pcmRing, drop, pcmRing, 0, filled - drop)
                            filled -= drop
                        }
                        for (i in 0 until count) pcmRing[filled + i] = shortBuffer[i] / 32768f
                        filled += count

                        val logmel = PannsMel.computeLogMel(pcmRing, melBasis)
                        val label = if (npu != null && npuIn != null && npuOut != null) {
                            classifyNpu(npu, npuIn, npuOut, logmel)
                        } else {
                            classifyInterpreter(interpreter, logmel, outputSize)
                        }
                        if (!label.isNullOrBlank()) {
                            _state.update { it.copy(transcript = label, errorMessage = null) }
                            onTranscriptChange?.invoke(label)
                        }
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio classification failed", e)
                setError(e.message ?: "Audio classification failed")
            } finally {
                releaseRecorder()
            }
        }
    }

    // Reuses the buffers created once in startRecorder — do not close them here.
    private fun classifyNpu(
        model: CompiledModel,
        input: TensorBuffer,
        output: TensorBuffer,
        logmel: FloatArray,
    ): String? {
        return runCatching {
            input.writeFloat(logmel)
            model.run(listOf(input), listOf(output))
            topLabels(output.readFloat())
        }.getOrNull()
    }

    private fun classifyInterpreter(
        interpreter: Interpreter,
        logmel: FloatArray,
        outputSize: Int,
    ): String? {
        return runCatching {
            val input = ByteBuffer.allocateDirect(logmel.size * 4).order(ByteOrder.nativeOrder())
            input.asFloatBuffer().put(logmel)
            val output = ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())
            interpreter.run(input, output)
            output.rewind()
            val probs = FloatArray(outputSize) { output.float }
            topLabels(probs)
        }.getOrNull()
    }

    private fun topLabels(probs: FloatArray): String? {
        val labels = labels()
        return probs.indices
            .sortedByDescending { probs[it] }
            .take(3)
            .joinToString(", ") { i ->
                val name = labels.getOrNull(i) ?: "class$i"
                "$name(%.0f%%)".format(probs[i].coerceIn(0f, 1f) * 100f)
            }
            .takeIf { it.isNotBlank() }
    }

    private fun labels(): List<String> {
        if (provider.labelsPath.isBlank()) return emptyList()
        return runCatching { File(provider.labelsPath).readLines().map { it.trim() } }
            .getOrDefault(emptyList())
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                errorMessage = message
            )
        }
    }

    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { npuInput?.close() }
        runCatching { npuOutput?.close() }
        runCatching { npuModel?.close() }
        npuInput = null
        npuOutput = null
        npuModel = null
        runCatching { interpreter?.close() }
        interpreter = null
    }
}
