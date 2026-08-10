package me.rerere.tts.provider.providers

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.tts.matcha.MatchaTtsBundle
import me.rerere.tts.matcha.MatchaTtsConfig
import me.rerere.tts.matcha.MatchaTtsEngine
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File

class MatchaTTSProvider : TTSProvider<TTSProviderSetting.MatchaTts> {
    private val mutex = Mutex()
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: Session? = null

    private data class SessionKey(val path: String, val config: MatchaTtsConfig)
    private data class Session(val key: SessionKey, val engine: MatchaTtsEngine)

    override val reusesEngine: Boolean get() = true

    override fun onSessionEnd() {
        val old = session
        session = null
        closeScope.launch { mutex.withLock { old?.engine?.close() } }
    }

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.MatchaTts,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        val text = request.text.trim()
        if (text.isEmpty()) {
            emit(emptyChunk())
            return@flow
        }
        val key = SessionKey(
            path = providerSetting.modelPath,
            config = MatchaTtsConfig(
                speechSpeed = providerSetting.speechSpeed,
                durationScale = providerSetting.durationScale,
                flowSteps = providerSetting.flowSteps,
                seed = providerSetting.seed,
            ),
        )
        val waveform = mutex.withLock {
            val active = session?.takeIf { it.key == key } ?: run {
                session?.engine?.close()
                val bundle = MatchaTtsBundle.open(File(key.path))
                Session(key, MatchaTtsEngine.create(bundle, key.config)).also { session = it }
            }
            try {
                active.engine.synthesize(text)
            } catch (error: Throwable) {
                if (session === active) {
                    session = null
                    active.engine.close()
                }
                throw error
            }
        }
        emit(
            AudioChunk(
                data = waveform.toPcm16(),
                format = AudioFormat.PCM,
                sampleRate = MatchaTtsEngine.SAMPLE_RATE,
                isLast = true,
                metadata = mapOf("provider" to "matcha-tts"),
            ),
        )
    }.flowOn(Dispatchers.IO)

    private fun emptyChunk() = AudioChunk(
        data = ByteArray(0),
        format = AudioFormat.PCM,
        sampleRate = MatchaTtsEngine.SAMPLE_RATE,
        isLast = true,
        metadata = mapOf("provider" to "matcha-tts"),
    )
}

private fun FloatArray.toPcm16(): ByteArray {
    val output = ByteArray(size * 2)
    val buffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
    for (sample in this) buffer.putShort((sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
    return output
}
