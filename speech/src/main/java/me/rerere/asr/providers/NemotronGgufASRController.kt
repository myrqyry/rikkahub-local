package me.rerere.asr.providers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
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
import me.rerere.asr.NemotronAudioChunker
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Experimental CPU streaming path backed by the pinned transcribe.cpp submodule.
 * AudioRecord never waits on model inference: a bounded queue prevents silent sample loss.
 * Inference and native session teardown are confined to a single worker coroutine.
 */
class NemotronGgufASRController(
    private val appContext: Context,
    private val provider: ASRProviderSetting.NemotronGguf,
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val worker = Dispatchers.IO.limitedParallelism(1, "NemotronAsr")
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
        if (!file.isFile || file.length() == 0L) {
            error("Nemotron GGUF not installed. Download or import a model in Settings → Speech.")
            return
        }
        val magic = runCatching { file.inputStream().use { input ->
            val prefix = ByteArray(4)
            if (input.read(prefix) == 4) prefix.decodeToString() else ""
        } }.getOrNull()
        if (magic != "GGUF") {
            error("The selected Nemotron file is not a GGUF model")
            return
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            error("Microphone permission required")
            return
        }
        if (provider.chunkMs != 1120 || provider.rightContext !in setOf(0, 3, 6, 13) ||
            provider.threads !in 1..12) {
            error("Unsupported Nemotron streaming settings")
            return
        }
        stopping.set(false)
        this.onTranscriptChange = onTranscriptChange
        _state.value = ASRState(status = ASRStatus.Connecting, isAvailable = true)
        val chunks = Channel<FloatArray>(capacity = 8)
        engineJob = scope.launch(worker) {
            var session = 0L
            try {
                session = NemotronNative.create(file.absolutePath, provider.language,
                    provider.threads, provider.rightContext)
                require(session != 0L) { "Nemotron session initialization failed" }
                val backend = NemotronNative.backend(session)
                Log.i("NemotronASR", "Loaded ${file.name} on actual backend=$backend")
                if (!stopping.get()) startCapture(chunks) else chunks.close()
                var hasAudio = false
                for (samples in chunks) {
                    hasAudio = true
                    val hypothesis = NemotronNative.feed(session, samples)
                    publish(hypothesis)
                }
                if (!hasAudio) {
                    _state.update { it.copy(status = ASRStatus.Idle) }
                    return@launch
                }
                if (!stopping.get()) stopping.set(true)
                publish(NemotronNative.finish(session))
                _state.update { it.copy(status = ASRStatus.Idle, errorMessage = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Throwable) {
                Log.e("NemotronASR", "Native ASR failed", cause)
                error(cause.message ?: "Nemotron recognition failed")
            } finally {
                // Never leave the microphone active after a native inference failure.
                stopping.set(true)
                runCatching { recorder?.stop() }
                captureJob?.cancel()
                if (session != 0L) NemotronNative.destroy(session)
            }
        }
    }

    @Suppress("MissingPermission")
    private fun startCapture(chunks: Channel<FloatArray>) {
        captureJob = scope.launch(Dispatchers.IO) {
            if (stopping.get()) { chunks.close(); return@launch }
            var audio: AudioRecord? = null
            try {
                val minSize = AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                require(minSize > 0) { "16 kHz PCM microphone not supported" }
                val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(8192, minSize * 2))
                audio = record
                require(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord initialization failed" }
                recorder = record
                if (stopping.get()) return@launch
                record.startRecording()
                _state.update { it.copy(status = ASRStatus.Listening) }
                val samples = ShortArray(2048)
                val chunker = NemotronAudioChunker(provider.chunkMs * 16) { frame ->
                    require(chunks.trySend(frame).isSuccess) {
                        "Nemotron inference fell behind microphone capture (bounded queue full)"
                    }
                }
                while (isActive && !stopping.get()) {
                    val count = record.read(samples, 0, samples.size)
                    if (count > 0) chunker.add(samples, count)
                    else if (!stopping.get()) throw IllegalStateException("Microphone read failed: $count")
                }
                chunker.finish()
                chunks.close()
            } catch (cancelled: CancellationException) {
                chunks.close(cancelled)
                throw cancelled
            } catch (cause: Throwable) {
                chunks.close(cause)
            } finally {
                recorder = null
                audio?.let { record ->
                    runCatching { if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop() }
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
        scope.cancel()
        onTranscriptChange = null
    }

    private fun error(message: String) {
        _state.update { it.copy(status = ASRStatus.Error, isAvailable = true, errorMessage = message) }
    }
}
