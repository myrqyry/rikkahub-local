package me.rerere.rikkahub.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.ai.StableDiffusionBridge
import me.rerere.rikkahub.data.ai.StableDiffusionProvider
import java.io.File

/**
 * Debug-only on-device generation verification hook (BuildConfig.DEBUG).
 * Drives StableDiffusionProvider.generateImage directly from adb so the 14-point
 * Vulkan acceptance matrix does not depend on the ImageGenScreen layout.
 *
 * Usage:
 *   adb shell am broadcast -n excp.rikkahub.local.debug/me.rerere.rikkahub.debug.SdGenTestHook \
 *     -a rikkahub.intent.action.SD_GEN_TEST \
 *     --ez vulkan true --ei steps 1 --ei width 512 --ei height 512 \
 *     --ei repeat 3 --ei cancelAfterMs 8000
 *
 * Output: PNG files written to the app external files dir (adb pull-able), timings
 * and SD-JNI/nativeGenerate lines to logcat (tag "SdGenTestHook").
 */
class SdGenTestHook : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "SdGenTestHook is debug-only; ignoring broadcast in a non-debug build")
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

        scope.launch {
            try {
                runHook(context, vulkan, steps, width, height, seed, repeat, cancelAfterMs, prompt)
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
    ) {
        val provider = StableDiffusionProvider(
            context = context.applicationContext,
            runtimePreferences = LocalRuntimePreferences(context.applicationContext),
        )
        val modelDir = File(context.applicationContext.filesDir, "local-models/stable-diffusion")
        val modelFile = modelDir.listFiles()
            ?.filter { it.extension.equals("gguf", ignoreCase = true) }
            ?.maxByOrNull { it.length() }
            ?: error("No Stable Diffusion GGUF installed; install one via Model Manager first")
        val model = Model(
            modelId = modelFile.name,
            displayName = modelFile.name,
            type = ModelType.IMAGE,
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.IMAGE),
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

        Log.i(TAG, "hook start backend=${if (vulkan) "vulkan" else "cpu"} steps=$steps ${width}x$height seed=$seed repeat=$repeat cancelAfterMs=$cancelAfterMs model=${modelFile.name}")

        repeat(repeat) { i ->
            StableDiffusionBridge.resetProgress()
            val runStart = System.currentTimeMillis()
            val first = i == 0
            var coldLoadMs = 0L
            var genMs = 0L
            var progressCount = 0
            var imageSaved = false
            var cancelled = false

            val runJob = scope.launch {
                provider.generateImage(providerSetting, ImageGenerationParams(model = model, prompt = prompt, numOfImages = 1))
                    .onEach { item: ImageGenerationItem ->
                        if (item.partial) {
                            progressCount++
                            Log.i(TAG, "hook run $i partial image index=${item.partialImageIndex}")
                        } else {
                            val now = System.currentTimeMillis()
                            if (first) coldLoadMs = now - runStart
                            genMs = now - runStart
                            val bytes = Base64.decode(item.data, Base64.NO_WRAP)
                            val file = File(outDir, "gen_${i}_${if (vulkan) "vulkan" else "cpu"}_${width}x${height}_s${steps}.png")
                            file.writeBytes(bytes)
                            imageSaved = true
                            Log.i(TAG, "hook run $i IMAGE saved ${file.absolutePath} bytes=${bytes.size}")
                        }
                    }
                    .collect()
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
                "hook run $i done first=$first coldLoadMs=$coldLoadMs genMs=$genMs progressCount=$progressCount imageSaved=$imageSaved cancelled=$cancelled",
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
        private const val TAG = "SdGenTestHook"
    }
}
