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
        Log.i(
            TAG,
            "hook start prompt=\"$prompt\" cancelAfterMs=$cancelAfterMs " +
                "modelPath=${modelPath ?: "auto-download from $DEFAULT_MODEL_URL"}",
        )
        scope.launch {
            try {
                runHook(prompt, cancelAfterMs, modelUrl, modelPath)
            } catch (t: Throwable) {
                Log.e(TAG, "hook failed", t)
            } finally {
                finish()
            }
        }
    }

    private suspend fun runHook(
        prompt: String,
        cancelAfterMs: Long,
        modelUrl: String,
        modelPath: String?,
    ) {
        val prefs = LocalRuntimePreferences(applicationContext)
        val provider = LlamaCppProvider(prefs = prefs)

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
        val messages = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text(prompt))),
        )
        val params = TextGenerationParams(
            model = model,
            temperature = 0.7f,
            maxTokens = 512,
        )

        val runStart = System.currentTimeMillis()
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
        val elapsedMs = System.currentTimeMillis() - runStart
        Log.i(
            TAG,
            "hook done model=${modelFile.name} chunks=$chunkCount " +
                "chars=${collected.length} firstDeltaMs=$firstDeltaMs " +
                "elapsedMs=$elapsedMs cancelled=$cancelled finishReason=$finishReason",
        )
        Log.i(TAG, "hook reply: ${collected.toString().take(400)}")
        Log.i(TAG, "hook complete")
    }

    companion object {
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_CANCEL_AFTER_MS = "cancelAfterMs"
        const val EXTRA_MODEL_URL = "modelUrl"
        const val EXTRA_MODEL_PATH = "modelPath"

        private const val TAG = "LlamaCppTestHook"
        private const val DEFAULT_MODEL_FILE = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        private const val DEFAULT_MODEL_URL =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/$DEFAULT_MODEL_FILE"
        private const val MIN_MODEL_BYTES = 100_000_000L
    }
}
