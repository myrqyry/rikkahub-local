package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.audio.texttospeech.SynthesisOptions
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeech
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeechOptions
import com.google.mediapipe.tasks.core.BaseOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.tts.mediapipe.MediaPipeTtsBundle
import me.rerere.tts.mediapipe.MediaPipeTtsCatalog
import me.rerere.tts.mediapipe.floatToPcm16
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

/**
 * Shared MediaPipe TextToSpeech backend for Phonon and Kokoro.
 *
 * The SDK integration, bundle metadata and PCM conversion are unit-tested;
 * real on-device inference and speaker output require separate acceptance.
 */
class MediaPipeTTSProvider : TTSProvider<TTSProviderSetting> {
    private data class SessionKey(val path: String, val speed: Float)
    private data class Session(val key: SessionKey, val engine: TextToSpeech)

    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var session: Session? = null

    override val reusesEngine: Boolean get() = true

    override fun onSessionEnd() {
        val old = session
        session = null
        // Native cancel is lock-free. Stop live synthesis before waiting for
        // the inference mutex to release the heavy native model.
        old?.let { runCatching { it.engine.cancel() } }
        if (old != null) {
            cleanupScope.launch {
                mutex.withLock {
                    runCatching { old.engine.close() }
                        .onFailure { Log.w("MediaPipeTTS", "Failed to release native engine", it) }
                }
            }
        }
    }

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        val text = request.text.trim()
        val (bundle, modelPath, voice, speed) = when (providerSetting) {
            is TTSProviderSetting.PhononTts -> Params(
                MediaPipeTtsCatalog.PHONON, providerSetting.modelPath,
                providerSetting.voice, providerSetting.speed,
            )
            is TTSProviderSetting.KokoroTts -> Params(
                MediaPipeTtsCatalog.KOKORO, providerSetting.modelPath,
                providerSetting.voice, providerSetting.speed,
            )
            else -> error("Unsupported MediaPipe TTS provider type")
        }
        if (text.isEmpty()) {
            emit(audioChunk(ByteArray(0), 24000, true, bundle))
            return@flow
        }
        require(bundle.voices.any { it.first == voice }) {
            "Unknown ${bundle.displayName} voice: $voice"
        }
        // Validate path, size, and pinned digest before passing model bytes to JNI.
        val file = bundle.bundleFile(modelPath)
        val key = SessionKey(file.absolutePath, speed)

        mutex.lock()
        try {
            currentCoroutineContext().ensureActive()
            val active = session?.takeIf { it.key == key } ?: run {
                session?.engine?.close()
                bundle.verifyFile(file)
                val options = TextToSpeechOptions(
                    baseOptions = BaseOptions.builder().setModelAssetPath(file.absolutePath).build(),
                    speakingRate = speed,
                )
                Session(key, TextToSpeech.create(options)).also { session = it }
            }
            var ended = false
            var frames = 0
            try {
                active.engine.synthesizeStream(text, SynthesisOptions(voiceId = voice))
                    .collect { chunk ->
                        currentCoroutineContext().ensureActive()
                        require(!ended) { "MediaPipe TTS emitted audio after final chunk" }
                        require(chunk.sampleRateHz == 24000) {
                            "Unexpected MediaPipe sample rate ${chunk.sampleRateHz}"
                        }
                        val samples = chunk.samples
                        frames += samples.size
                        if (samples.isNotEmpty() || chunk.isFinal) {
                            emit(audioChunk(floatToPcm16(samples), chunk.sampleRateHz, chunk.isFinal, bundle))
                        }
                        if (chunk.isFinal) ended = true
                    }
                check(frames > 0) { "${bundle.displayName} produced no audio" }
                if (!ended) emit(audioChunk(ByteArray(0), 24000, true, bundle))
            } catch (error: Throwable) {
                if (session === active) session = null
                runCatching { active.engine.cancel() }
                runCatching { active.engine.close() }
                throw error
            }
        } finally {
            mutex.unlock()
        }
    }.flowOn(Dispatchers.IO)

    private data class Params(
        val bundle: MediaPipeTtsBundle,
        val modelPath: String,
        val voice: String,
        val speed: Float,
    )

    private fun audioChunk(
        data: ByteArray, rate: Int, isLast: Boolean, bundle: MediaPipeTtsBundle,
    ) = AudioChunk(
        data = data,
        format = AudioFormat.PCM,
        sampleRate = rate,
        isLast = isLast,
        metadata = mapOf("provider" to bundle.engineId),
    )
}
