package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Provider for the local-dream native Stable Diffusion server.
 *
 * local-dream is a native C++ HTTP server (not a JNI library) managed via
 * [ProcessBuilder]. It runs on a configurable port (default 8081) and exposes
 * an SSE-based HTTP API for image generation. The native executable is shipped
 * as `libstable_diffusion_core.so` in the native library directory.
 */
class LocalDreamProvider(
    private val context: android.content.Context,
    private val json: Json,
) : Provider<ProviderSetting.LocalDream> {

    private var backendProcess: Process? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for SSE streaming
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun listModels(providerSetting: ProviderSetting.LocalDream): List<Model> {
        return listOf(
            Model(
                modelId = providerSetting.modelId,
                displayName = providerSetting.modelId,
            )
        )
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.LocalDream,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = throw UnsupportedOperationException("LocalDream does not support text generation")

    override suspend fun streamText(
        providerSetting: ProviderSetting.LocalDream,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = flow {
        throw UnsupportedOperationException("LocalDream does not support text generation")
    }

    override suspend fun generateEmbedding(
        providerSetting: ProviderSetting.LocalDream,
        params: EmbeddingGenerationParams,
    ): EmbeddingGenerationResult = throw UnsupportedOperationException("LocalDream does not support embeddings")

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): Flow<ImageGenerationItem> = flow {
        require(providerSetting is ProviderSetting.LocalDream) {
            "Expected LocalDream provider setting"
        }
        ensureBackendRunning(providerSetting)

        val requestBody = json.encodeToString(buildJsonObject {
            put("prompt", params.prompt)
            put("negative_prompt", "")
            put("steps", providerSetting.steps)
            put("cfg", providerSetting.cfg)
            put("width", providerSetting.width)
            put("height", providerSetting.height)
            put("seed", -1)
            put("output_format", "png")
        })

        val request = Request.Builder()
            .url("http://127.0.0.1:${providerSetting.port}/generate")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        val body = response.body?.string() ?: return@flow

        parseSseResponse(body).forEach { emit(it) }

        // Keep the process alive for reuse; caller manages lifecycle
    }

    override suspend fun editImage(
        providerSetting: ProviderSetting,
        params: ImageEditParams,
    ): Flow<ImageGenerationItem> = error("Image edit is not yet supported")

    // ---- Private helpers ----

    private fun parseSseResponse(body: String): List<ImageGenerationItem> {
        val images = mutableListOf<ImageGenerationItem>()
        val events = body.split("\n\n")
        for (event in events) {
            if (event.startsWith("event: complete")) {
                val dataLine = event.lines().find { it.startsWith("data: ") } ?: continue
                val dataStr = dataLine.removePrefix("data: ")
                val data = json.parseToJsonElement(dataStr).jsonObject
                val imageBase64 = data["image"]?.jsonPrimitive?.content ?: continue
                val format = data["format"]?.jsonPrimitive?.content ?: "png"

                images.add(
                    ImageGenerationItem(
                        data = imageBase64,
                        mimeType = "image/$format",
                    )
                )
            }
        }
        return images
    }

    private fun ensureBackendRunning(settings: ProviderSetting.LocalDream) {
        if (backendProcess?.isAlive == true) return
        backendProcess = startBackendProcess(settings)
    }

    private fun startBackendProcess(settings: ProviderSetting.LocalDream): Process {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val executable = File(nativeDir, "libstable_diffusion_core.so")
        if (!executable.exists()) {
            throw IllegalStateException("local-dream native library not found: $executable")
        }

        val modelsDir = File(context.filesDir, "models/${settings.modelId}")
        val runtimeDir = File(context.filesDir, "runtime_libs")

        val command = mutableListOf(
            executable.absolutePath,
            "--type", settings.backendType,
            "--model_dir", modelsDir.absolutePath,
            "--port", settings.port.toString(),
        )
        if (runtimeDir.exists()) {
            command += listOf("--lib_dir", runtimeDir.absolutePath)
        }

        val pb = ProcessBuilder(command)
            .directory(File(nativeDir))
            .redirectErrorStream(true)

        return pb.start()
    }
}