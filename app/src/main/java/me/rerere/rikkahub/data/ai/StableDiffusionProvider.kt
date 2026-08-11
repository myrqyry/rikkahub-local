package me.rerere.rikkahub.data.ai

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.EmbeddingGenerationResult
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences
import me.rerere.locallm.SdCatalog
import me.rerere.locallm.SdGenerationProfile
import java.io.ByteArrayOutputStream
import java.io.File

class StableDiffusionProvider(
    private val context: Context = org.koin.java.KoinJavaComponent.getKoin().get(),
    private val runtimePreferences: LocalRuntimePreferences =
        org.koin.java.KoinJavaComponent.getKoin().get(),
    private val bridge: StableDiffusionBridge = StableDiffusionBridge,
) : Provider<ProviderSetting.StableDiffusion> {

    init {
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                    bridge.invalidateSession()
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            override fun onLowMemory() {
                bridge.invalidateSession()
            }
        })
    }

    override suspend fun listModels(providerSetting: ProviderSetting.StableDiffusion): List<Model> {
        return providerSetting.models
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.StableDiffusion,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = throw UnsupportedOperationException("StableDiffusion does not support text generation")

    override suspend fun streamText(
        providerSetting: ProviderSetting.StableDiffusion,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = flow {
        throw UnsupportedOperationException("StableDiffusion does not support text generation")
    }

    override suspend fun generateEmbedding(
        providerSetting: ProviderSetting.StableDiffusion,
        params: EmbeddingGenerationParams,
    ): EmbeddingGenerationResult = throw UnsupportedOperationException("StableDiffusion does not support embeddings")

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): Flow<ImageGenerationItem> = flow {
        require(providerSetting is ProviderSetting.StableDiffusion) {
            "Expected StableDiffusion provider setting"
        }

        // Clear any stale progress from a previous run before this generation starts.
        bridge.resetProgress()

        // The model passed by the normal Provider API is the source of truth. Resolve its file
        // through the same LocalRuntimePreferences inventory that Model Manager writes instead of
        // relying on a second, independently-mutated currentModelPath field.
        val modelPath = resolveInstalledModelPath(providerSetting, params.model)
        val effective = resolveEffectiveGenerationParams(
            providerSetting = providerSetting,
            profile = SdCatalog.findByModelFile(params.model.modelId)?.generationProfile,
        )
        stableDiffusionRequestError(
            width = effective.width,
            height = effective.height,
            steps = effective.steps,
            cfg = effective.cfgScale,
        )?.let { throw IllegalStateException(it) }

        var initialized = false
        try {
            bridge.ensureLoaded()

            val backend = if (providerSetting.useVulkan) {
                StableDiffusionBridge.Backend.VULKAN
            } else {
                StableDiffusionBridge.Backend.CPU
            }
            if (!bridge.nativeSupportsBackend(backend.value)) {
                throw IllegalStateException(
                    when (backend) {
                        StableDiffusionBridge.Backend.VULKAN ->
                            "Vulkan acceleration is not compiled into this build. Switch Stable Diffusion to CPU."
                        StableDiffusionBridge.Backend.CPU ->
                            "The CPU Stable Diffusion backend is not available in this build."
                    }
                )
            }

            initialized = withContext(nativeDispatcher) {
                bridge.ensureSession(modelPath, backend)
            }
            if (!initialized) {
                throw IllegalStateException(
                    if (providerSetting.useVulkan) {
                        "Failed to initialize the Vulkan image backend."
                    } else {
                        "Failed to load the image model. Check the file or try a different model."
                    }
                )
            }

            val rgba = generateNativeWithCancellation(
                prompt = params.prompt,
                negativePrompt = providerSetting.negativePrompt,
                width = effective.width,
                height = effective.height,
                steps = effective.steps,
                cfg = effective.cfgScale,
                seed = providerSetting.seed,
            ) ?: throw IllegalStateException(
                "Generation failed or was cancelled by the native runtime. Check model compatibility and available memory."
            )

            val expectedBytes = effective.width.toLong() * effective.height.toLong() * 4L
            if (expectedBytes > Int.MAX_VALUE || rgba.size != expectedBytes.toInt()) {
                throw IllegalStateException(
                    "Native image output had ${rgba.size} bytes; expected $expectedBytes RGBA bytes."
                )
            }

            val pngBytes = rgbaToPng(rgba, effective.width, effective.height)
            val b64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
            emit(ImageGenerationItem(data = b64, mimeType = "image/png"))
        } catch (e: UnsatisfiedLinkError) {
            throw IllegalStateException(
                "Image generation is not available on this device (arm64 native runtime required)",
                e,
            )
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Generation timed out and was cancelled. Try fewer steps or a smaller image/model.",
                e,
            )
        } catch (e: CancellationException) {
            // User/app cancellation is normal coroutine control flow. Never turn it into a fake
            // generation failure; generateNativeWithCancellation already tells sd.cpp to stop.
            throw e
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Generation error: ${e.message ?: e::class.simpleName}", e)
        }
    }

    private suspend fun resolveInstalledModelPath(
        providerSetting: ProviderSetting.StableDiffusion,
        model: Model,
    ): String {
        val installed = runtimePreferences.installedModels(LocalRuntime.StableDiffusion)
        val inventoryPath = installed[model.modelId]
        if (inventoryPath != null && File(inventoryPath).isFile) {
            return inventoryPath
        }

        // Compatibility for installs created before the runtime inventory became authoritative.
        // Only accept the legacy path when its basename matches the selected model, so choosing
        // model B can never silently run model A just because currentModelPath is stale.
        val legacyPath = providerSetting.currentModelPath
        if (
            legacyPath != null &&
            File(legacyPath).isFile &&
            File(legacyPath).name == model.modelId
        ) {
            return legacyPath
        }

        throw IllegalStateException(
            "Selected local image model '${model.displayName.ifBlank { model.modelId }}' is not installed. " +
                "Re-import it in Model Manager or select another image model."
        )
    }

    private suspend fun generateNativeWithCancellation(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfg: Float,
        seed: Int,
    ): ByteArray? = coroutineScope {
        // Keep the blocking JNI call outside the timeout child. If the timeout fires, awaiting it
        // becomes cancellable while the native call remains alive long enough for nativeCancel()
        // to flip sd.cpp's atomic cancellation flag. We then wait for C++ to unwind before the
        // warm native session is reused.
        val nativeCall = async(nativeDispatcher) {
            bridge.nativeGenerate(
                prompt = prompt,
                negativePrompt = negativePrompt,
                width = width,
                height = height,
                steps = steps,
                cfg = cfg,
                seed = seed,
            )
        }
        try {
            withTimeout(GENERATION_TIMEOUT_MS) {
                nativeCall.await()
            }
        } catch (e: CancellationException) {
            bridge.nativeCancel()
            withContext(NonCancellable) {
                nativeCall.join()
            }
            throw e
        }
    }

    override suspend fun editImage(
        providerSetting: ProviderSetting,
        params: ImageEditParams,
    ): Flow<ImageGenerationItem> = error("Image edit is not yet supported")

    private companion object {
        const val GENERATION_TIMEOUT_MS = 120_000L
        val nativeDispatcher = Dispatchers.Default.limitedParallelism(1, "StableDiffusionNative")
    }
}

internal fun stableDiffusionRequestError(
    width: Int,
    height: Int,
    steps: Int,
    cfg: Float,
): String? = when {
    width !in 64..2048 || height !in 64..2048 ->
        "Image width and height must be between 64 and 2048 pixels."
    width % 8 != 0 || height % 8 != 0 ->
        "Image width and height must be multiples of 8."
    steps !in 1..200 ->
        "Sampling steps must be between 1 and 200."
    !cfg.isFinite() || cfg !in 0f..50f ->
        "CFG scale must be a finite value between 0 and 50."
    else -> null
}

/** The resolved width/height/steps/CFG actually sent to the native runtime. */
internal data class EffectiveGenerationParams(
    val width: Int,
    val height: Int,
    val steps: Int,
    val cfgScale: Float,
)

/**
 * Resolves model-aware generation defaults.
 *
 * Catalog models carry an [SdGenerationProfile] with model-appropriate values — the
 * 1-to-4-step distilled Turbo families must not inherit the generic 20-step / CFG-7
 * defaults of ordinary SD/SDXL. A provider field still holding the generic factory
 * default counts as "unset" and falls back to the model profile; any other value is a
 * deliberate user override and wins as-is.
 */
internal fun resolveEffectiveGenerationParams(
    providerSetting: ProviderSetting.StableDiffusion,
    profile: SdGenerationProfile?,
): EffectiveGenerationParams {
    if (profile == null) {
        return EffectiveGenerationParams(
            width = providerSetting.width,
            height = providerSetting.height,
            steps = providerSetting.steps,
            cfgScale = providerSetting.cfgScale,
        )
    }
    val factory = ProviderSetting.StableDiffusion()
    return EffectiveGenerationParams(
        width = if (providerSetting.width == factory.width) profile.defaultWidth else providerSetting.width,
        height = if (providerSetting.height == factory.height) profile.defaultHeight else providerSetting.height,
        steps = if (providerSetting.steps == factory.steps) profile.defaultSteps else providerSetting.steps,
        cfgScale = if (providerSetting.cfgScale == factory.cfgScale) profile.defaultCfgScale else providerSetting.cfgScale,
    )
}

// bridge.cpp guarantees exactly width*height*4 RGBA bytes regardless of the channel count
// returned by stable-diffusion.cpp. Keep the conversion here so the provider continues to emit
// the same PNG/base64 contract as cloud image providers.
private fun rgbaToPng(rgba: ByteArray, width: Int, height: Int): ByteArray {
    val expectedBytes = width.toLong() * height.toLong() * 4L
    require(expectedBytes <= Int.MAX_VALUE && rgba.size == expectedBytes.toInt()) {
        "Invalid RGBA buffer length ${rgba.size}; expected $expectedBytes"
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val offset = i * 4
            val r = rgba[offset].toInt() and 0xff
            val g = rgba[offset + 1].toInt() and 0xff
            val b = rgba[offset + 2].toInt() and 0xff
            val a = rgba[offset + 3].toInt() and 0xff
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return ByteArrayOutputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                "Bitmap PNG encoding failed"
            }
            out.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}
