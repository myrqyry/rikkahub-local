package me.rerere.locallm.llamacpp

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import java.io.File
import java.lang.ref.WeakReference
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * One global Llamatik engine exists in the process. Chat, vision, embedding, and benchmark
 * must not race over its model or generation parameters. Never hold the gate in a UI callback.
 */
internal object GgufNativeGate {
    val mutex = Mutex()
    private val providers = mutableListOf<WeakReference<LlamaCppProvider>>()

    @Synchronized
    fun register(provider: LlamaCppProvider) {
        providers.removeAll { it.get() == null }
        providers.add(WeakReference(provider))
    }

    /** Caller must hold [mutex]; closes cached sessions *before* switching native model. */
    @Synchronized
    fun invalidateForSweep() {
        providers.removeAll { it.get() == null }
        providers.forEach { it.get()?.closeCachedSessionsForTuning() }
    }
}

@Serializable
data class GgufTuningConfig(
    val threads: Int,
    val flashAttention: Boolean = true,
)

@Serializable
data class GgufTuningSample(
    val config: GgufTuningConfig,
    val firstOutputMs: Double,
    /** Measured Unicode code points per second after first callback. Not token/s. */
    val codepointsPerSecond: Double,
    val producedCodepoints: Int,
)

@Serializable
data class GgufTuningRecord(
    val fingerprint: String,
    val baseline: GgufTuningSample,
    val winner: GgufTuningSample,
    val samples: List<GgufTuningSample>,
    val measuredAtMs: Long,
    val applied: Boolean = false,
)

object GgufTuningPolicy {
    private const val RUNTIME_ID = "llamatik-1.10.1:gguf-tuner-v1"

    fun fingerprint(file: File): String {
        require(file.isFile && file.length() > 0) { "GGUF model must exist" }
        val source = listOf(
            file.canonicalPath, file.length().toString(), file.lastModified().toString(),
            Build.SUPPORTED_ABIS.firstOrNull().orEmpty(), Build.FINGERPRINT, RUNTIME_ID,
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun threadCandidates(processors: Int): List<GgufTuningConfig> {
        val count = processors.coerceAtLeast(1)
        // Preserve the pre-tuning runtime default as a control; avoid benchmarking
        // flash-attention toggles until both native paths have device qualification.
        return (listOf(count, 2, 4, 6) + listOf((count / 2).coerceAtLeast(1)))
            .filter { it in 1..count }.distinct().map { GgufTuningConfig(it) }
    }

    fun choose(samples: List<GgufTuningSample>): GgufTuningSample {
        require(samples.isNotEmpty())
        require(samples.all {
            it.firstOutputMs.isFinite() && it.firstOutputMs > 0 &&
                it.codepointsPerSecond.isFinite() && it.codepointsPerSecond > 0 &&
                it.producedCodepoints >= 48
        }) { "Incomplete or invalid generated benchmark data" }
        val bestRate = samples.maxOf { it.codepointsPerSecond }
        val bestFirst = samples.minOf { it.firstOutputMs }
        return samples.maxWith(
            compareBy<GgufTuningSample> {
                0.65 * (it.codepointsPerSecond / bestRate) +
                    0.35 * (bestFirst / it.firstOutputMs)
            }.thenBy { -it.config.threads }
        )
    }
}

/**
 * Explicit, user-triggered benchmark. Real generated output only: no synthetic timing.
 * The native bridge does not expose tokenizer counts, so don't report tokens/second.
 */
class GgufAutoTuner(private val context: Context) {
    suspend fun sweep(
        model: File,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): GgufTuningRecord = withContext(Dispatchers.IO) {
        require(model.isFile && model.extension.equals("gguf", ignoreCase = true)) {
            "Choose an installed GGUF model"
        }
        val fingerprint = GgufTuningPolicy.fingerprint(model)
        val configs = GgufTuningPolicy.threadCandidates(Runtime.getRuntime().availableProcessors())
        check(GgufNativeGate.mutex.tryLock()) {
            "Another local GGUF inference session is running. Finish it before tuning."
        }
        try {
            GgufNativeGate.invalidateForSweep()
            val samples = mutableListOf<GgufTuningSample>()
            val total = configs.size * 2
            for ((index, config) in configs.withIndex()) {
                val repetitions = mutableListOf<GgufTuningSample>()
                repeat(2) { repetition ->
                    checkBatteryAndTemperature()
                    val measured = runOnce(model, config, index * 2 + repetition)
                    repetitions.add(measured)
                    onProgress(index * 2 + repetition + 1, total)
                }
                samples.add(
                    GgufTuningSample(
                        config,
                        repetitions.map { it.firstOutputMs }.average(),
                        repetitions.map { it.codepointsPerSecond }.average(),
                        repetitions.minOf { it.producedCodepoints },
                    )
                )
            }
            check(GgufTuningPolicy.fingerprint(model) == fingerprint) {
                "GGUF model changed during the benchmark; results discarded"
            }
            val winner = GgufTuningPolicy.choose(samples)
            GgufTuningRecord(
                fingerprint = fingerprint,
                baseline = samples.first(),
                winner = winner,
                samples = samples,
                measuredAtMs = System.currentTimeMillis(),
            )
        } finally {
            // Never leave an experimental native model/context attached to Chat.
            runCatching { LlamaBridge.shutdown() }
            GgufNativeGate.mutex.unlock()
        }
    }

    private fun runOnce(model: File, config: GgufTuningConfig, runIndex: Int): GgufTuningSample {
        // Freeze generation settings across all candidates. No KV-quant or GPU variants:
        // Llamatik 1.10.1 does not expose those controls.
        // Llamatik applies thread/attention settings at model initialization,
        // not on an existing context. Force a real reload for every candidate.
        LlamaBridge.shutdown()
        LlamaBridge.updateGenerateParams(
            temperature = 0f, maxTokens = 96, topP = 1f, topK = 1,
            repeatPenalty = 1f, contextLength = 1024,
            numThreads = config.threads, useMmap = true,
            flashAttention = config.flashAttention, batchSize = 256, gpuLayers = 0,
        )
        check(LlamaBridge.initGenerateModel(model.absolutePath)) {
            "Failed to initialize GGUF for ${config.threads}-thread benchmark"
        }
        val session = LlamaBridge.createSession("tuner-$runIndex")
            ?: error("Failed to create GGUF benchmark session")
        try {
            var firstNs = 0L
            var lastNs = 0L
            var codepoints = 0
            var error: String? = null
            val start = SystemClock.elapsedRealtimeNanos()
            session.stream(BENCH_PROMPT, object : GenStream {
                override fun onDelta(text: String) {
                    if (text.isEmpty()) return
                    val at = SystemClock.elapsedRealtimeNanos()
                    if (firstNs == 0L) firstNs = at
                    lastNs = at
                    codepoints += text.codePointCount(0, text.length)
                }
                override fun onComplete() = Unit
                override fun onError(message: String) { error = message }
            })
            check(error == null) { "GGUF benchmark failed: $error" }
            check(codepoints >= 48 && firstNs > start && lastNs >= firstNs) {
                "Model stopped too early for a meaningful result (${codepoints} characters)"
            }
            // First-output latency includes actual prompt evaluation. Total time and generated
            // output rate are separate, not proxy token counts.
            val firstMs = (firstNs - start) / 1e6
            val elapsedDecodeSeconds = ((SystemClock.elapsedRealtimeNanos() - firstNs) / 1e9)
                .coerceAtLeast(0.001)
            return GgufTuningSample(config, firstMs, codepoints / elapsedDecodeSeconds, codepoints)
        } finally {
            session.close()
        }
    }

    private fun checkBatteryAndTemperature() {
        val status = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            check(100 * level / scale >= 25) { "Battery under 25%; tuning stopped" }
        }
        val tempDeciC = status.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        if (tempDeciC > 0) {
            check(tempDeciC < 410) { "Battery temperature at or above 41 C; tuning stopped" }
        }
    }

    private companion object {
        const val BENCH_PROMPT = "Write a numbered list of twenty clear, distinct ways to maintain " +
            "a small home garden. Each item must be a complete sentence and contain at least twelve words."
    }
}
