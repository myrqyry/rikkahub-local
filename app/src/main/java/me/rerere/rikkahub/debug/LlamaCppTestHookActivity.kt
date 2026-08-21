package me.rerere.rikkahub.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences
import me.rerere.locallm.ModelInstall
import me.rerere.locallm.llamacpp.LlamaCppProvider
import me.rerere.rikkahub.BuildConfig
import okhttp3.OkHttpClient
import java.io.File

/**
 * Debug-only verification hook for the llama.cpp (Llamatik) provider, driven from adb:
 *
 *   adb shell am start -n excp.rikkahub.local.debug/.debug.LlamaCppTestHookActivity \
 *     --es prompt "hello" --el cancelAfterMs 15000
 *
 * Downloads a small GGUF chat model if none is registered under the llama-cpp runtime,
 * then streams a reply through [LlamaCppProvider.streamText] and logs per-chunk deltas.
 * Optional [EXTRA_CANCEL_AFTER_MS] cancels the stream mid-generation to exercise
 * cancellation. An Activity is used instead of a BroadcastReceiver because the Android 17
 * preview on the test device stalls the broadcast queue (see SESSION-STATE.md).
 */
class LlamaCppTestHookActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "LlamaCppTestHook is debug-only; ignoring launch in non-debug builds")
            finish()
            return
        }
        val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: "What is the capital of France? Answer in one sentence."
        val cancelAfterMs = intent.getLongExtra(EXTRA_CANCEL_AFTER_MS, 0L)
        val modelUrl = intent.getStringExtra(EXTRA_MODEL_URL) ?: DEFAULT_MODEL_URL
        val modelPath = intent.getStringExtra(EXTRA_MODEL_PATH)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_CHAT
        val imagePath = intent.getStringExtra(EXTRA_IMAGE)
        val multiTurn = intent.getIntExtra(EXTRA_MULTI_TURN, 0)
        Log.i(
            TAG,
            "hook start mode=$mode prompt=\"$prompt\" cancelAfterMs=$cancelAfterMs multiTurn=$multiTurn " +
                "modelPath=${modelPath ?: "auto-download from $DEFAULT_MODEL_URL"} " +
                "image=${imagePath ?: "none"}",
        )
        scope.launch {
            try {
                runHook(mode, prompt, cancelAfterMs, modelUrl, modelPath, imagePath, multiTurn)
            } catch (t: Throwable) {
                Log.e(TAG, "hook failed", t)
            } finally {
                finish()
            }
        }
    }

    private suspend fun runHook(
        mode: String,
        prompt: String,
        cancelAfterMs: Long,
        modelUrl: String,
        modelPath: String?,
        imagePath: String?,
        multiTurn: Int,
    ) {
        val prefs = LocalRuntimePreferences(applicationContext)
        val provider = LlamaCppProvider(context = applicationContext, prefs = prefs)

        var modelFile = if (!modelPath.isNullOrBlank()) {
            File(modelPath).takeIf { it.isFile }
        } else {
            val modelDir = File(applicationContext.filesDir, "local-models/llama-cpp")
            modelDir.listFiles()
                ?.filter { it.extension.equals("gguf", ignoreCase = true) }
                ?.maxByOrNull { it.length() }
        }

        if (modelFile == null) {
            val target = ModelInstall.targetFile(
                ModelInstall.localModelsDir(applicationContext),
                LocalRuntime.LlamaCpp,
                DEFAULT_MODEL_FILE,
            )
            Log.i(TAG, "model missing — downloading $DEFAULT_MODEL_FILE")
            val downloadClient = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
                .writeTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
                .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
                .build()
            var completed = false
            ModelInstall.download(downloadClient, modelUrl, target).collect { progress ->
                when (progress) {
                    is ModelInstall.Progress.Tick ->
                        Log.i(TAG, "download ${progress.bytesRead}/${progress.totalBytes ?: "?"}")
                    is ModelInstall.Progress.Done -> {
                        completed = true
                        Log.i(TAG, "download complete: ${progress.file.name}")
                    }
                    is ModelInstall.Progress.Failed ->
                        Log.e(TAG, "download failed", progress.cause)
                    is ModelInstall.Progress.Started -> Unit
                }
            }
            if (!completed || !target.isFile || target.length() < MIN_MODEL_BYTES) {
                error("download did not complete: ${target.absolutePath}")
            }
            modelFile = target
        }

        prefs.addInstalledModel(
            LocalRuntime.LlamaCpp,
            modelFile.name,
            modelFile.absolutePath,
        )

        val model = Model(
            modelId = modelFile.name,
            displayName = modelFile.name,
        )
        val providerSetting = ProviderSetting.LlamaCppLocal(
            enabled = true,
            models = listOf(model),
        )
        val messages = buildList {
            add(
                UIMessage(
                    role = MessageRole.USER,
                    parts = buildList {
                        add(UIMessagePart.Text(prompt))
                        if (!imagePath.isNullOrBlank()) {
                            add(UIMessagePart.Image(url = "file://$imagePath"))
                        }
                    },
                ),
            )
        }
        val params = TextGenerationParams(
            model = model,
            temperature = 0.7f,
            maxTokens = 512,
        )

        if (mode == MODE_EMBED) {
            val embedRunStart = System.currentTimeMillis()
            val result = provider.generateEmbedding(
                providerSetting,
                me.rerere.ai.provider.EmbeddingGenerationParams(
                    model = model,
                    input = listOf(prompt, "Second probe sentence for embedding."),
                ),
            )
            val elapsedMs = System.currentTimeMillis() - embedRunStart
            val dims = result.embeddings.map { it.size }
            Log.i(TAG, "embed done model=${modelFile.name} count=${result.embeddings.size} dims=$dims elapsedMs=$elapsedMs")
            Log.i(TAG, "embed sample: ${result.embeddings.firstOrNull()?.take(8)}")
            Log.i(TAG, "hook complete")
            return
        }

        val runStart = System.currentTimeMillis()

        if (multiTurn > 0) {
            val q2 = "Now what is 2 + 2? Answer in one sentence."
            val r1 = streamOnce(provider, providerSetting, params, messages, runStart, 0L)
            Log.i(TAG, "multiturn turn1 chunks=${r1.chunks} chars=${r1.collected.length} firstDeltaMs=${r1.firstDeltaMs} elapsedMs=${r1.elapsedMs} finishReason=${r1.finishReason}")
            Log.i(TAG, "multiturn turn1 reply: ${r1.collected.take(400)}")
            val turn2Messages = messages +
                UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.Text(r1.collected.toString())),
                ) +
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(q2)),
                )
            val r2 = streamOnce(provider, providerSetting, params, turn2Messages, System.currentTimeMillis(), 0L)
            Log.i(TAG, "multiturn turn2 chunks=${r2.chunks} chars=${r2.collected.length} firstDeltaMs=${r2.firstDeltaMs} elapsedMs=${r2.elapsedMs} finishReason=${r2.finishReason}")
            Log.i(TAG, "multiturn turn2 reply: ${r2.collected.take(400)}")
            val continued = r2.collected.contains("4") || r2.collected.contains("four")
            Log.i(TAG, "multiturn continuation applied (turn2 answered q2, not re-answering turn1)=$continued")
            Log.i(TAG, "hook complete")
            return
        }

        val r = streamOnce(provider, providerSetting, params, messages, runStart, cancelAfterMs)
        Log.i(
            TAG,
            "hook done model=${modelFile.name} chunks=${r.chunks} " +
                "chars=${r.collected.length} firstDeltaMs=${r.firstDeltaMs} " +
                "elapsedMs=${r.elapsedMs} cancelled=${r.cancelled} finishReason=${r.finishReason}",
        )
        Log.i(TAG, "hook reply: ${r.collected.take(400)}")
        Log.i(TAG, "hook complete")
    }

    private suspend fun streamOnce(
        provider: LlamaCppProvider,
        providerSetting: ProviderSetting.LlamaCppLocal,
        params: TextGenerationParams,
        messages: List<UIMessage>,
        runStart: Long,
        cancelAfterMs: Long,
    ): StreamResult {
        var chunkCount = 0
        var firstDeltaMs: Long = -1
        var finishReason: String? = null
        val collected = StringBuilder()

        val runJob = scope.launch {
            provider.streamText(providerSetting, messages, params)
                .onEach { chunk: MessageChunk ->
                    chunkCount++
                    val text = chunk.choices.firstOrNull()?.delta?.parts
                        ?.filterIsInstance<UIMessagePart.Text>()
                        ?.joinToString("") { it.text }
                        .orEmpty()
                    if (text.isNotEmpty()) {
                        if (firstDeltaMs < 0) firstDeltaMs = System.currentTimeMillis() - runStart
                        collected.append(text)
                    }
                    chunk.choices.firstOrNull()?.finishReason?.let { finishReason = it }
                }
                .collect()
        }
        runJob.invokeOnCompletion { t ->
            if (t != null) Log.e(TAG, "stream job failed", t)
        }

        val cancelJob = if (cancelAfterMs > 0L) {
            scope.launch {
                delay(cancelAfterMs)
                Log.i(TAG, "hook cancelling stream after ${cancelAfterMs}ms")
                runJob.cancel()
            }
        } else {
            null
        }

        runJob.join()
        val cancelled = runJob.isCancelled
        cancelJob?.cancel()
        return StreamResult(
            chunks = chunkCount,
            firstDeltaMs = firstDeltaMs,
            collected = collected.toString(),
            finishReason = finishReason,
            cancelled = cancelled,
            elapsedMs = System.currentTimeMillis() - runStart,
        )
    }

    private data class StreamResult(
        val chunks: Int,
        val firstDeltaMs: Long,
        val collected: String,
        val finishReason: String?,
        val cancelled: Boolean,
        val elapsedMs: Long,
    )

    companion object {
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_CANCEL_AFTER_MS = "cancelAfterMs"
        const val EXTRA_MODEL_URL = "modelUrl"
        const val EXTRA_MODEL_PATH = "modelPath"
        const val EXTRA_MODE = "mode"
        const val EXTRA_IMAGE = "image"
        const val EXTRA_MULTI_TURN = "multiTurn"

        const val MODE_CHAT = "chat"
        const val MODE_EMBED = "embed"

        private const val TAG = "LlamaCppTestHook"
        private const val DEFAULT_MODEL_FILE = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        private const val DEFAULT_MODEL_URL =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/$DEFAULT_MODEL_FILE"
        private const val MIN_MODEL_BYTES = 100_000_000L
    }
}
