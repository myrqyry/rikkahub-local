package me.rerere.tts.provider.providers

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.pocket.PocketTtsBundle
import me.rerere.tts.pocket.PocketTtsConfig
import me.rerere.tts.pocket.PocketTtsEngine
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fully local Pocket TTS (Kyutai 100M) via the soniqo/Pocket-TTS-100M-ONNX-INT8
 * five-graph ONNX bundle. Streams 80 ms PCM frames at 24 kHz as they decode.
 * English only, fixed baked voice (no cloning).
 */
class PocketTTSProvider : TTSProvider<TTSProviderSetting.PocketTts> {
    private val mutex = Mutex()
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var sessionPath: String? = null
    private var session: Session? = null

    private class Session(val bundle: PocketTtsBundle, val engine: PocketTtsEngine)

    override val reusesEngine: Boolean
        get() = true

    override fun onSessionStart(providerSetting: TTSProviderSetting.PocketTts) {
        sessionPath = providerSetting.modelPath
    }

    override fun onSessionEnd() {
        val s = session
        session = null
        sessionPath = null
        closeScope.launch {
            mutex.withLock { s?.bundle?.close() }
        }
    }

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.PocketTts,
        request: TTSRequest
    ): Flow<AudioChunk> = callbackFlow {
        launch(Dispatchers.IO) {
            try {
                mutex.withLock {
                    val s = session?.takeIf { sessionPath == providerSetting.modelPath }
                        ?: run {
                            session?.bundle?.close()
                            sessionPath = providerSetting.modelPath
                            val directory = File(providerSetting.modelPath)
                            val bundle = PocketTtsBundle.open(directory)
                            val engine = PocketTtsEngine.create(
                                directory = directory,
                                bundle = bundle,
                                config = PocketTtsConfig(
                                    flowSteps = providerSetting.flowSteps,
                                    temperature = providerSetting.temperature,
                                    maxFrames = providerSetting.maxFrames,
                                    framesAfterEos = providerSetting.framesAfterEos,
                                    eosThreshold = providerSetting.eosThreshold,
                                    intraThreads = providerSetting.intraThreads,
                                    seed = providerSetting.seed,
                                ),
                            )
                            Session(bundle, engine).also { session = it }
                        }
                    try {
                        s.engine.synthesize(request.text) { frame ->
                            val pcm = ByteBuffer.allocate(frame.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (sample in frame) {
                                pcm.putShort(
                                    (sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                )
                            }
                            trySend(
                                AudioChunk(
                                    data = pcm.array(),
                                    format = AudioFormat.PCM,
                                    sampleRate = PocketTtsEngine.SAMPLE_RATE,
                                    metadata = mapOf("provider" to "pocket-tts"),
                                )
                            )
                        }
                    } catch (e: Throwable) {
                        if (session === s) {
                            session = null
                            s.bundle.close()
                        }
                        throw e
                    }
                }
                trySend(
                    AudioChunk(
                        data = ByteArray(0),
                        format = AudioFormat.PCM,
                        sampleRate = PocketTtsEngine.SAMPLE_RATE,
                        isLast = true,
                        metadata = mapOf("provider" to "pocket-tts"),
                    )
                )
                close()
            } catch (e: Throwable) {
                close(e)
            }
        }
        awaitClose { }
    }
}
