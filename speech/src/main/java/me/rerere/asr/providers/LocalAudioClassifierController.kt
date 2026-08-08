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
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "LocalAudioClassifier"

/**
 * Local on-device audio classifier (TFLite Task Library AudioClassifier).
 *
 * Slots into the existing ASRController surface so it shows up in the speech
 * settings picker like the streaming providers: `start()` records PCM16 via
 * [AudioRecord], streams it into the model's [org.tensorflow.lite.support.audio.TensorAudio]
 * ring buffer, and emits the top-3 classification labels as the "transcript".
 *
 * Model files (e.g. `cnn14_audioset_fp16.tflite` or `w2v2_frontend_fp16.tflite`)
 * are link-only imports via Settings → Speech → Local Audio Classifier; without a
 * model installed the controller fails closed with a clear message.
 */
class LocalAudioClassifierController(
    private val appContext: Context,
    private val provider: ASRProviderSetting.LocalAudioClassifier,
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var classifier: AudioClassifier? = null
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
        recorderJob = scope.launch(Dispatchers.IO) {
            val classifier = runCatching { AudioClassifier.createFromFile(modelFile) }
                .getOrElse { e ->
                    Log.e(TAG, "Failed to load audio classifier model", e)
                    setError("Failed to load audio classifier model: ${e.message}")
                    return@launch
                }
            this@LocalAudioClassifierController.classifier = classifier

            val requiredFormat = classifier.requiredTensorAudioFormat
            val tensorAudio = classifier.createInputTensorAudio()
            val captureSampleRate = requiredFormat.sampleRate.coerceIn(8000, 48000)
            val channels = requiredFormat.channels.coerceAtLeast(1)
            val channelMask = if (channels == 2) {
                AudioFormat.CHANNEL_IN_STEREO
            } else {
                AudioFormat.CHANNEL_IN_MONO
            }

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

            try {
                recorder.startRecording()
                val shortBuffer = ShortArray(bufferSize / 2)
                val byteBuffer = ByteArray(bufferSize)
                while (isActive) {
                    val read = recorder.read(byteBuffer, 0, byteBuffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(byteBuffer, read)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }
                        // bytes -> shorts for the ring buffer
                        ByteBuffer.wrap(byteBuffer, 0, read)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(shortBuffer, 0, read / 2)
                        tensorAudio.load(shortBuffer, 0, read / 2)
                        val label = classifier
                            .classify(tensorAudio)
                            .firstOrNull()
                            ?.categories
                            ?.take(3)
                            ?.joinToString(", ") { c ->
                                val name = c.label.ifBlank { c.displayName ?: "unknown" }
                                "$name(%.0f%%)".format(c.score * 100f)
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
        runCatching { classifier?.close() }
        classifier = null
    }
}
