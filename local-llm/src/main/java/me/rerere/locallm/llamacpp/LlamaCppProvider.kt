package me.rerere.locallm.llamacpp

import com.llamatik.library.platform.LlamaBridge
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
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
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences

class LlamaCppProvider(
    private val prefs: LocalRuntimePreferences,
) : Provider<ProviderSetting.LlamaCppLocal> {

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

        val request = LlamaCppPrompt.build(messages)
        val streamId = "llamacpp-${System.currentTimeMillis()}"

        val job = launch(Dispatchers.IO) {
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
            if (!LlamaBridge.initGenerateModel(modelPath)) {
                close(IllegalStateException("Failed to load model \"${params.model.modelId}\" with llama.cpp"))
                return@launch
            }

            var nativeError: String? = null
            try {
                LlamaBridge.generateWithContextStream(
                    system = request.system,
                    context = request.context,
                    user = request.user,
                    onDelta = { delta ->
                        if (delta.isNotEmpty()) {
                            trySend(deltaChunk(streamId, params.model.modelId, delta))
                        }
                    },
                    onDone = { },
                    onError = { nativeError = it },
                )
                if (nativeError != null) {
                    close(IllegalStateException("llama.cpp inference failed: $nativeError"))
                } else {
                    trySend(stopChunk(streamId, params.model.modelId))
                    close()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                close(e)
            } finally {
                LlamaBridge.nativeCancelGenerate()
            }
        }

        awaitClose {
            LlamaBridge.nativeCancelGenerate()
            job.cancel()
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
