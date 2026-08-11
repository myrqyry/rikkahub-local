package me.rerere.rikkahub.data.ai

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
import java.io.ByteArrayOutputStream
import java.io.File

class StableDiffusionProvider(
    private val bridge: StableDiffusionBridge = StableDiffusionBridge,
) : Provider<ProviderSetting.StableDiffusion> {

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

        val modelPath = providerSetting.currentModelPath
        if (modelPath == null || !File(modelPath).isFile) {
            throw IllegalStateException("Model file not found: $modelPath")
        }
        stableDiffusionRequestError(
            width = providerSetting.width,
            height = providerSetting.height,
            steps = providerSetting.steps,
            cfg = providerSetting.cfgScale,
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
                bridge.nativeInit(modelPath, backend.value)
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
                width = providerSetting.width,
                height = providerSetting.height,
                steps = providerSetting.steps,
                cfg = providerSetting.cfgScale,
                seed = providerSetting.seed,
            ) ?: throw IllegalStateException(
                "Generation failed or was cancelled by the native runtime. Check model compatibility and available memory."
            )

            val expectedBytes = providerSetting.width.toLong() * providerSetting.height.toLong() * 4L
            if (expectedBytes > Int.MAX_VALUE || rgba.size != expectedBytes.toInt()) {
                throw IllegalStateException(
                    "Native image output had ${rgba.size} bytes; expected $expectedBytes RGBA bytes."
                )
            }

            val pngBytes = rgbaToPng(rgba, providerSetting.width, providerSetting.height)
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
        } finally {
            if (initialized) {
                withContext(NonCancellable + nativeDispatcher) {
                    bridge.nativeRelease()
                }
            }
        }
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
        // provider is allowed to free the context in finally.
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
