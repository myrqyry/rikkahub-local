package me.rerere.rikkahub.data.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
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
        if (modelPath == null || !File(modelPath).exists()) {
            throw IllegalStateException("Model file not found: $modelPath")
        }

        try {
            bridge.ensureLoaded()

            val backend = if (providerSetting.useVulkan) StableDiffusionBridge.Backend.VULKAN
            else StableDiffusionBridge.Backend.CPU

            val initOk = withContext(Dispatchers.Default) {
                bridge.nativeInit(modelPath, backend.value)
            }
            if (!initOk) {
                throw IllegalStateException(
                    if (providerSetting.useVulkan) "GPU acceleration failed. Check if your device supports Vulkan."
                    else "Failed to load model. Check the file or try a different model."
                )
            }

            val rgba = withContext(Dispatchers.Default) {
                withTimeout(120_000L) {
                    bridge.nativeGenerate(
                        prompt = params.prompt,
                        negativePrompt = providerSetting.negativePrompt,
                        width = providerSetting.width,
                        height = providerSetting.height,
                        steps = providerSetting.steps,
                        cfg = providerSetting.cfgScale,
                        seed = providerSetting.seed,
                    )
                }
            }

            bridge.nativeRelease()

            if (rgba == null) {
                throw IllegalStateException("Generation failed. Not enough memory or model error.")
            }

            val pngBytes = rgbaToPng(rgba, providerSetting.width, providerSetting.height)
            val b64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)

            emit(ImageGenerationItem(data = b64, mimeType = "image/png"))
        } catch (e: UnsatisfiedLinkError) {
            throw IllegalStateException("Image generation is not available on this device (arm64 required)")
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Generation timed out. Try fewer steps or a smaller model.")
        } catch (e: Exception) {
            throw IllegalStateException("Generation error: ${e.message}")
        }
    }

    override suspend fun editImage(
        providerSetting: ProviderSetting,
        params: ImageEditParams,
    ): Flow<ImageGenerationItem> = error("Image edit is not yet supported")
}

// ponytail: RGBA bytes (bridge returns width*height*4) to PNG via Android Bitmap.
// Upgrade to native PNG encoding in bridge.cpp if throughput matters.
private fun rgbaToPng(rgba: ByteArray, width: Int, height: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)
    for (i in 0 until width * height) {
        val offset = i * 4
        val r = rgba[offset].toInt() and 0xff
        val g = rgba[offset + 1].toInt() and 0xff
        val b = rgba[offset + 2].toInt() and 0xff
        pixels[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return out.toByteArray()
}
