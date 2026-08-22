package me.rerere.rikkahub.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.GeneratedImagePayload
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.rikkahub.data.media.writePayloadToFile
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences
import me.rerere.locallm.ModelInstall
import me.rerere.locallm.SdCatalog
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.ai.StableDiffusionBridge
import me.rerere.rikkahub.data.ai.StableDiffusionProvider
import okhttp3.OkHttpClient
import java.io.File

class SdGenTestHook : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "SdGenTestHook is debug-only; ignoring broadcast in non-debug builds")
            return
        }
        val vulkan = intent.getBooleanExtra(EXTRA_VULKAN, true)
        val steps = intent.getIntExtra(EXTRA_STEPS, 1)
        val width = intent.getIntExtra(EXTRA_WIDTH, 512)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 512)
        val seed = intent.getIntExtra(EXTRA_SEED, -1)
        val repeat = intent.getIntExtra(EXTRA_REPEAT, 1).coerceAtLeast(1)
        val cancelAfterMs = intent.getLongExtra(EXTRA_CANCEL_AFTER_MS, 0L)
        val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: "a red apple on a wooden table"
        val modelPath = intent.getStringExtra(EXTRA_MODEL_PATH)
        scope.launch {
            try {
                runHook(context, vulkan, steps, width, height, seed, repeat, cancelAfterMs, prompt, modelPath)
            } catch (t: Throwable) {
                Log.e(TAG, "hook failed", t)
            }
        }
    }

    private suspend fun runHook(
        context: Context,
        vulkan: Boolean,
        steps: Int,
        width: Int,
        height: Int,
        seed: Int,
        repeat: Int,
        cancelAfterMs: Long,
        prompt: String,
        modelPath: String? = null,
    ) {
        val provider = StableDiffusionProvider(
            context = context.applicationContext,
            runtimePreferences = LocalRuntimePreferences(context.applicationContext),
        )

        var modelFile = if (!modelPath.isNullOrBlank()) {
            File(modelPath).takeIf { it.isFile }
        } else {
            val modelDir = File(context.applicationContext.filesDir, "local-models/stable-diffusion")
            modelDir.listFiles()
                ?.filter { it.extension.equals("gguf", ignoreCase = true) }
                ?.maxByOrNull { it.length() }
        }

        if (modelFile == null) {
            val entry = SdCatalog.ENTRIES[0]
            val target = ModelInstall.targetFile(
                ModelInstall.localModelsDir(context),
                LocalRuntime.StableDiffusion,
                entry.modelFile,
            )
            Log.i(TAG, "model missing — downloading ${entry.modelFile}")
val downloadClient = OkHttpClient.Builder()
    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
    .writeTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
    .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
    .build()
            ModelInstall.download(downloadClient, entry.resolveUrl(), target).collect { progress ->
                when (progress) {
                    is ModelInstall.Progress.Tick ->
                        Log.i(TAG, "download ${progress.bytesRead}/${progress.totalBytes ?: "?"}")
                    is ModelInstall.Progress.Done ->
                        Log.i(TAG, "download complete: ${progress.file.name}")
                    is ModelInstall.Progress.Failed ->
                        Log.e(TAG, "download failed", progress.cause)
                    is ModelInstall.Progress.Started -> Unit
                }
            }
            if (!target.isFile || target.length() < 2_000_000_000L) {
                error("download did not complete: ${target.absolutePath}")
            }
            modelFile = target
        }

        val model = Model(
            modelId = modelFile.name,
            displayName = modelFile.name,
            type = ModelType.IMAGE,
            inputModalities = listOf(Modality.TEXT),
            outputModalities = listOf(Modality.IMAGE),
        )
        val providerSetting = ProviderSetting.StableDiffusion(
            enabled = true,
            models = listOf(model),
            useVulkan = vulkan,
            width = width,
            height = height,
            steps = steps,
            seed = seed,
            currentModelPath = modelFile.absolutePath,
        )
        val outDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "sd-test").apply { mkdirs() }
        Log.i(
            TAG,
            "hook start backend=${if (vulkan) "vulkan" else "cpu"} steps=$steps ${width}x$height " +
                "seed=$seed repeat=$repeat cancelAfterMs=$cancelAfterMs model=${modelFile.name} path=${modelFile.absolutePath}",
        )

        repeat(repeat) { i ->
            StableDiffusionBridge.resetProgress()
            val runStart = System.currentTimeMillis()
            val first = i == 0
            var coldLoadMs: Long = 0
            var genMs: Long = 0
            var progressCount = 0
            var imageSaved = false
            var cancelled = false

            val runJob = scope.launch {
                provider.generateImage(
                    providerSetting,
                    ImageGenerationParams(model = model, prompt = prompt, numOfImages = 1),
                ).onEach { item: ImageGenerationItem ->
                    if (item.partial) {
                        progressCount++
                        Log.i(TAG, "hook run $i partial image index=${item.partialImageIndex}")
                    } else {
                        val now = System.currentTimeMillis()
                        if (first) coldLoadMs = now - runStart
                        genMs = now - runStart
                        val file = File(
                            outDir,
                            "gen_${i}_${if (vulkan) "vulkan" else "cpu"}_${width}x${height}_s${steps}.png",
                        )
                        writePayloadToFile(item.payload, file)
                        imageSaved = true
                        Log.i(TAG, "hook run $i IMAGE saved ${file.absolutePath}")
                    }
                }.collect()
            }
            runJob.invokeOnCompletion { t ->
                if (t != null) Log.e(TAG, "runJob $i failed", t)
            }

            val cancelJob = if (cancelAfterMs > 0L) {
                scope.launch {
                    kotlinx.coroutines.delay(cancelAfterMs)
                    Log.i(TAG, "hook cancelling run $i after ${cancelAfterMs}ms")
                    runJob.cancel()
                }
            } else {
                null
            }

            runJob.join()
            cancelled = runJob.isCancelled
            cancelJob?.cancel()
            Log.i(
                TAG,
                "hook run $i done first=$first coldLoadMs=$coldLoadMs genMs=$genMs " +
                    "progressCount=$progressCount imageSaved=$imageSaved cancelled=$cancelled",
            )
        }
        Log.i(TAG, "hook complete")
    }

    companion object {
        const val ACTION = "rikkahub.intent.action.SD_GEN_TEST"
        const val EXTRA_VULKAN = "vulkan"
        const val EXTRA_STEPS = "steps"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_SEED = "seed"
        const val EXTRA_REPEAT = "repeat"
        const val EXTRA_CANCEL_AFTER_MS = "cancelAfterMs"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_MODEL_PATH = "modelPath"
        private const val TAG = "SdGenTestHook"
    }
}
