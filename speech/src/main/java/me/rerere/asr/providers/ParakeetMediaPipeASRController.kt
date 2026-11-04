package me.rerere.asr.providers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriber
import com.google.mediapipe.tasks.core.BaseOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
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
import me.rerere.asr.ParakeetSegment
import me.rerere.asr.ParakeetTranscriptAccumulator
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device MediaPipe Audio Transcriber (Parakeet LiteRT-LM).
 *
 * The microphone runs separately from model inference. A bounded PCM queue fails
 * explicitly if inference falls behind rather than silently dropping samples.
 * stop() drains recorded audio and calls finishStream(); dispose() cancels work.
 * No network requests or model downloads happen in this controller.
 */
class ParakeetMediaPipeASRController(
    private val appContext: Context,
    private val provider: ASRProviderSetting.ParakeetMediaPipe,
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val worker = Dispatchers.IO.limitedParallelism(1, "MediaPipeParakeet")
    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()
    private val stopping = AtomicBoolean(false)
    private var captureJob: Job? = null
    private var engineJob: Job? = null
    @Volatile private var recorder: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        val file = File(provider.modelPath)
        if (provider.modelPath.isBlank() || !file.isFile || file.length() == 0L ||
            file.extension.lowercase() != "litertlm"
        ) {
            error("Parakeet LiteRT-LM model not found. Download it in Settings → Speech.")
            return
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            error("Microphone permission required")
            return
        }

        stopping.set(false)
        this.onTranscriptChange = onTranscriptChange
        _state.value = ASRState(status = ASRStatus.Connecting, isAvailable = true)
        val pcm = Channel<FloatArray>(capacity = 32)
        engineJob = scope.launch(worker) {
            var engine: AudioTranscriber? = null
            try {
                val options = AudioTranscriber.AudioTranscriberOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(file.absolutePath).build())
                    .setSampleRateHz(16000)
                    .setEnableInterimResults(true)
                    .build()
                engine = AudioTranscriber.createFromOptions(appContext, options)
                engine.prewarm()
                if (stopping.get()) {
                    pcm.close()
                    return@launch
                }
                startCapture(pcm)
                val transcript = ParakeetTranscriptAccumulator()
                var receivedAudio = false
                for (samples in pcm) {
                    receivedAudio = true
                    val result = engine.transcribe(samples, false)
                    publish(transcript.add(result.segments().map { segment ->
                        ParakeetSegment(
                            segment.fromTimestampMs(), segment.toTimestampMs(),
                            segment.transcript(), segment.isFinal(),
                        )
                    }))
                }
                if (receivedAudio) {
                    val final = engine.finishStream()
                    publish(transcript.add(final.segments().map { segment ->
                        ParakeetSegment(
                            segment.fromTimestampMs(), segment.toTimestampMs(),
                            segment.transcript(), segment.isFinal(),
                        )
                    }))
                }
                _state.update { it.copy(status = ASRStatus.Idle, errorMessage = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Throwable) {
                Log.e("MediaPipeParakeet", "On-device ASR failed", cause)
                error(cause.message ?: "MediaPipe Parakeet transcription failed")
            } finally {
                stopping.set(true)
                runCatching { recorder?.stop() }
                captureJob?.cancel()
                engine?.close()
            }
        }
    }

    @Suppress("MissingPermission")
    private fun startCapture(pcm: Channel<FloatArray>) {
        captureJob = scope.launch(Dispatchers.IO) {
            var audio: AudioRecord? = null
            try {
                if (stopping.get()) return@launch
                val minSize = AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                require(minSize > 0) { "16 kHz microphone recording unsupported" }
                val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(12800, minSize * 2))
                audio = record
                require(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord initialization failed" }
                recorder = record
                if (stopping.get()) return@launch
                record.startRecording()
                _state.update { it.copy(status = ASRStatus.Listening) }
                val samples = ShortArray(3200)
                while (isActive && !stopping.get()) {
                    val count = record.read(samples, 0, samples.size)
                    if (count > 0) {
                        val floats = FloatArray(count) { i -> samples[i] / 32768.0f }
                        require(pcm.trySend(floats).isSuccess) {
                            "Parakeet inference fell behind microphone capture (bounded queue full)"
                        }
                    } else if (!stopping.get()) {
                        throw IllegalStateException("Microphone read failed: $count")
                    }
                }
            } catch (cancelled: CancellationException) {
                pcm.close(cancelled)
                throw cancelled
            } catch (cause: Throwable) {
                pcm.close(cause)
            } finally {
                pcm.close()
                recorder = null
                audio?.let { record ->
                    runCatching {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                    }
                    record.release()
                }
            }
        }
    }

    private fun publish(transcript: String) {
        if (transcript.isBlank()) return
        _state.update { it.copy(transcript = transcript) }
        scope.launch(Dispatchers.Main.immediate) { onTranscriptChange?.invoke(transcript) }
    }

    override fun stop() {
        if (!state.value.isRecording) return
        stopping.set(true)
        _state.update { it.copy(status = ASRStatus.Stopping) }
        runCatching { recorder?.stop() }
    }

    override fun dispose() {
        stopping.set(true)
        runCatching { recorder?.stop() }
        captureJob?.cancel()
        engineJob?.cancel()
        onTranscriptChange = null
        scope.cancel()
    }

    private fun error(message: String) {
        _state.update {
            it.copy(status = ASRStatus.Error, isAvailable = true, errorMessage = message)
        }
    }
}
