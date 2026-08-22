package me.rerere.locallm.llamacpp

import android.content.Context
import android.graphics.Bitmap
import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import com.llamatik.library.platform.LlamaSession
import com.llamatik.library.platform.MultimodalBridge
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.EmbeddingGenerationResult
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.toBitmap
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences

class LlamaCppProvider(
    private val context: Context,
    private val prefs: LocalRuntimePreferences,
) : Provider<ProviderSetting.LlamaCppLocal> {

    private val sessions = ConcurrentHashMap<String, LlamaSession>()
    private val histories = ConcurrentHashMap<String, List<String>>()
    private val activeSessions = ConcurrentHashMap<String, LlamaSession>()

    override suspend fun listModels(providerSetting: ProviderSetting.LlamaCppLocal): List<Model> {
        return prefs.installedModels(LocalRuntime.LlamaCpp)
            .map { (fileName, _) -> Model(modelId = fileName, displayName = fileName) }
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.LlamaCppLocal,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk {
        val collected = StringBuilder()
        var finishReason: String? = null
        streamText(providerSetting, messages, params).collect { chunk ->
            chunk.choices.firstOrNull()?.delta?.parts
                ?.filterIsInstance<UIMessagePart.Text>()
                ?.forEach { collected.append(it.text) }
            if (chunk.choices.firstOrNull()?.finishReason != null) {
                finishReason = chunk.choices.first().finishReason
            }
        }
        return MessageChunk(
            id = "llamacpp-${System.currentTimeMillis()}",
            model = params.model.modelId,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text(collected.toString())),
                    ),
                    finishReason = finishReason ?: "stop",
                ),
            ),
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.LlamaCppLocal,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = callbackFlow {
        val installed = prefs.installedModels(LocalRuntime.LlamaCpp)
        val modelPath = installed[params.model.modelId]
            ?: throw IllegalStateException("Model ${params.model.modelId} not installed")
        if (!File(modelPath).exists()) {
            throw IllegalStateException(
                "Model file for \"${params.model.modelId}\" is no longer present on disk ($modelPath). " +
                    "Delete the model entry in Settings → On-device models and re-download it.",
            )
        }

        val streamId = "llamacpp-${System.currentTimeMillis()}"

        val imageParts = messages
            .flatMap { it.parts }
            .filterIsInstance<UIMessagePart.Image>()

        val job = if (imageParts.isEmpty()) {
            launch(Dispatchers.IO) {
                streamTextNative(modelPath, messages, providerSetting, params, streamId)
            }
        } else {
            launch(Dispatchers.IO) {
                streamVisionNative(modelPath, messages, imageParts, params, streamId)
            }
        }

        awaitClose {
            LlamaBridge.nativeCancelGenerate()
            MultimodalBridge.cancelAnalysis()
            activeSessions.values.forEach { it.cancel() }
            job.cancel()
        }
    }

    private suspend fun ProducerScope<MessageChunk>.streamTextNative(
        modelPath: String,
        messages: List<UIMessage>,
        providerSetting: ProviderSetting.LlamaCppLocal,
        params: TextGenerationParams,
        streamId: String,
    ) {
        val request = LlamaCppPrompt.build(messages)
        val incoming = LlamaCppPrompt.rawTexts(messages)
        val modelId = params.model.modelId

        LlamaBridge.updateGenerateParams(
            temperature = providerSetting.temperature?.toFloat() ?: params.temperature ?: DEFAULT_TEMPERATURE,
            maxTokens = params.maxTokens ?: DEFAULT_MAX_TOKENS,
            topP = providerSetting.topP?.toFloat() ?: params.topP ?: DEFAULT_TOP_P,
            topK = providerSetting.topK ?: DEFAULT_TOP_K,
            repeatPenalty = DEFAULT_REPEAT_PENALTY,
            contextLength = DEFAULT_CONTEXT_LENGTH,
            numThreads = Runtime.getRuntime().availableProcessors(),
            useMmap = true,
            flashAttention = true,
            batchSize = DEFAULT_BATCH_SIZE,
            gpuLayers = 0,
        )

        val stored = histories[modelId] ?: emptyList()
        val continueSession = LlamaSessionContinuation.shouldContinue(stored, incoming)
        if (continueSession) {
            val session = sessions[modelId]
            if (session != null) {
                val text = LlamaSessionContinuation.continuationText(incoming)
                if (text != null) {
                    streamSession(session, text, streamId, modelId, incoming)
                    return
                }
            }
        }

        sessions.remove(modelId)?.close()
        if (!LlamaBridge.initGenerateModel(modelPath)) {
            close(IllegalStateException("Failed to load model \"${params.model.modelId}\" with llama.cpp"))
            return
        }
        val session = LlamaBridge.createSession(modelId)
            ?: run {
                close(IllegalStateException("Failed to create a llama.cpp session for \"${params.model.modelId}\""))
                return
            }
        sessions[modelId] = session
        val fullPrompt = buildString {
            if (request.system.isNotEmpty()) append(request.system).append("\n")
            if (request.context.isNotEmpty()) append(request.context)
            if (request.user.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(request.user)
            }
        }
        streamSession(session, fullPrompt, streamId, modelId, incoming)
    }

    private suspend fun ProducerScope<MessageChunk>.streamSession(
        session: LlamaSession,
        prompt: String,
        streamId: String,
        modelId: String,
        incoming: List<String>,
    ) {
        activeSessions[modelId] = session
        val generated = StringBuilder()
        var nativeError: String? = null
        try {
            session.stream(
                prompt,
                object : GenStream {
                    override fun onDelta(text: String) {
                        if (text.isNotEmpty()) {
                            generated.append(text)
                            trySend(deltaChunk(streamId, modelId, text))
                        }
                    }

                    override fun onComplete() = Unit

                    override fun onError(message: String) {
                        nativeError = message
                    }
                },
            )
            if (nativeError != null) {
                sessions.remove(modelId)?.close()
                close(IllegalStateException("llama.cpp inference failed: $nativeError"))
            } else {
                histories[modelId] = incoming + generated.toString()
                trySend(stopChunk(streamId, modelId))
                close()
            }
        } catch (e: Exception) {
            sessions.remove(modelId)?.close()
            if (e is CancellationException) throw e
            close(e)
        } finally {
            activeSessions.remove(modelId)
            session.cancel()
        }
    }

    private suspend fun ProducerScope<MessageChunk>.streamVisionNative(
        modelPath: String,
        messages: List<UIMessage>,
        imageParts: List<UIMessagePart.Image>,
        params: TextGenerationParams,
        streamId: String,
    ) {
        val mmproj = MmprojLocator.findMmproj(File(modelPath))
            ?: run {
                close(
                    IllegalStateException(
                        "Model \"${params.model.modelId}\" needs a vision projector (.mmproj.gguf) next to it to " +
                            "answer with images. Place one beside the model file, then retry.",
                    ),
                )
                return
            }

        val prompt = LlamaCppPrompt.build(messages).let { request ->
            request.user.ifEmpty { request.context }
        }

        val imageBytes = imageParts.mapNotNull { image ->
            image.toBitmap(context)?.toPngByteArray()
        }
        if (imageBytes.isEmpty()) {
            close(IllegalStateException("Could not decode the attached image(s)."))
            return
        }

        if (!MultimodalBridge.initModel(modelPath, mmproj.absolutePath)) {
            close(IllegalStateException("Failed to load vision model \"${params.model.modelId}\" with llama.cpp"))
            return
        }

        var nativeError: String? = null
        try {
            MultimodalBridge.analyzeImageBytesStream(
                imageBytes = imageBytes.first(),
                prompt = prompt,
                callback = object : GenStream {
                    override fun onDelta(text: String) {
                        if (text.isNotEmpty()) {
                            trySend(deltaChunk(streamId, params.model.modelId, text))
                        }
                    }

                    override fun onComplete() = Unit

                    override fun onError(message: String) {
                        nativeError = message
                    }
                },
            )
            if (nativeError != null) {
                close(IllegalStateException("llama.cpp vision inference failed: $nativeError"))
            } else {
                trySend(stopChunk(streamId, params.model.modelId))
                close()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            close(e)
        } finally {
            MultimodalBridge.release()
        }
    }

    override suspend fun generateEmbedding(
        providerSetting: ProviderSetting.LlamaCppLocal,
        params: EmbeddingGenerationParams,
    ): EmbeddingGenerationResult {
        require(params.input.isNotEmpty()) { "input must not be empty" }
        val installed = prefs.installedModels(LocalRuntime.LlamaCpp)
        val modelPath = installed[params.model.modelId]
            ?: throw IllegalStateException("Model ${params.model.modelId} not installed")
        if (!File(modelPath).exists()) {
            throw IllegalStateException(
                "Model file for \"${params.model.modelId}\" is no longer present on disk ($modelPath). " +
                    "Delete the model entry in Settings → On-device models and re-download it.",
            )
        }
        return withContext(Dispatchers.IO) {
            if (!LlamaBridge.initEmbedModel(modelPath)) {
                throw IllegalStateException("Failed to load embedding model \"${params.model.modelId}\" with llama.cpp")
            }
            val embeddings = params.input.map { input ->
                LlamaBridge.embed(input).toList()
            }
            EmbeddingGenerationResult(model = params.model.modelId, embeddings = embeddings)
        }
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): Flow<ImageGenerationItem> = error("llama.cpp does not support image generation")

    private fun deltaChunk(streamId: String, model: String, delta: String): MessageChunk {
        return MessageChunk(
            id = streamId,
            model = model,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text(delta)),
                    ),
                    message = null,
                    finishReason = null,
                ),
            ),
        )
    }

    private fun stopChunk(streamId: String, model: String): MessageChunk {
        return MessageChunk(
            id = streamId,
            model = model,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(role = MessageRole.ASSISTANT, parts = emptyList()),
                    message = null,
                    finishReason = "stop",
                ),
            ),
        )
    }

    private fun Bitmap.toPngByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    companion object {
        private const val DEFAULT_TEMPERATURE = 0.8f
        private const val DEFAULT_TOP_P = 0.95f
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_MAX_TOKENS = 2048
        private const val DEFAULT_REPEAT_PENALTY = 1.1f
        private const val DEFAULT_CONTEXT_LENGTH = 4096
        private const val DEFAULT_BATCH_SIZE = 512
    }
}
