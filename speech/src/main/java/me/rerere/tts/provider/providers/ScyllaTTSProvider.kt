package me.rerere.tts.provider.providers

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.scylla.ScyllaTtsCatalog
import org.scyllasband.android.ScyllasBand
import org.scyllasband.android.ScyllasBandAccelerator
import org.scyllasband.android.ScyllasBandDelivery
import org.scyllasband.android.ScyllasBandSegmentSettings

/** Native LiteRT TTS. Audio is synthesized on CPU and delivered as real PCM chunks. */
class ScyllaTTSProvider : TTSProvider<TTSProviderSetting.ScyllaTts> {
    private val mutex = Mutex()
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class Session(val dir: String, val runtime: ScyllasBand)
    @Volatile private var session: Session? = null
    override val reusesEngine: Boolean get() = true

    override fun onSessionEnd() {
        val old = session ?: return
        // Detach synchronously: a new session must not reuse an engine in teardown.
        session = null
        old.runtime.cancel()
        closeScope.launch {
            mutex.withLock { old.runtime.close() }
        }
    }

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.ScyllaTts,
        request: TTSRequest,
    ): Flow<AudioChunk> = channelFlow {
        val text = request.text.trim()
        if (text.isEmpty()) {
            trySend(AudioChunk(ByteArray(0), AudioFormat.PCM, ScyllaTtsCatalog.sampleRate, true))
            return@channelFlow
        }
        val dir = providerSetting.modelPath.takeIf { it.isNotBlank() }
            ?: error("Install the Scylla's Band LiteRT model first")
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = session?.takeIf { it.dir == dir } ?: run {
                    session?.runtime?.close()
                    session = null
                    val missing = ScyllaTtsCatalog.verify(File(dir))
                    require(missing.isEmpty()) {
                        "Scylla bundle missing or invalid: ${missing.take(3).joinToString()}"
                    }
                    val engine = ScyllasBand.create(context, File(dir))
                    try {
                        engine.initialize(threadCount = 4, accelerator = ScyllasBandAccelerator.CPU)
                    } catch (cause: Throwable) {
                        engine.close()
                        throw cause
                    }
                    Session(dir, engine).also { session = it }
                }
                try {
                    val settings = ScyllasBandSegmentSettings(
                        providerSetting.voice,
                        providerSetting.language,
                        ScyllasBandDelivery(
                            energy = providerSetting.energy,
                            tension = providerSetting.tension,
                            valence = providerSetting.valence,
                            assertiveness = providerSetting.assertiveness,
                        ),
                    )
                    val completed = current.runtime.synthesizeStreaming(
                        text = text,
                        settings = settings,
                        seed = providerSetting.seed,
                        onChunkStarted = { _, _, _ -> isActive },
                        onAudioChunk = { samples, index, count ->
                            isActive && trySendBlocking(
                                AudioChunk(
                                    data = samples.toScyllaPcm16(),
                                    format = AudioFormat.PCM,
                                    sampleRate = ScyllaTtsCatalog.sampleRate,
                                    isLast = index == count - 1,
                                    metadata = mapOf(
                                        "provider" to "scyllasband",
                                        "voice" to providerSetting.voice,
                                    ),
                                ),
                            ).isSuccess
                        },
                    )
                    if (!completed && isActive) error("Scylla stopped before completing synthesis")
                } catch (cause: Throwable) {
                    if (session === current) {
                        session = null
                        current.runtime.close()
                    }
                    throw cause
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}

private fun FloatArray.toScyllaPcm16(): ByteArray =
    ByteBuffer.allocate(size * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
        for (sample in this@toScyllaPcm16) {
            require(sample.isFinite()) { "Scylla emitted non-finite PCM" }
            putShort((sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
    }.array()
