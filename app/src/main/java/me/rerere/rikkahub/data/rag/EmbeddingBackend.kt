package me.rerere.rikkahub.data.rag

import java.io.File
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.reranker.QwenEmbedder
import me.rerere.reranker.QwenEngineRegistry

/**
 * RAG embedding strategy. [EmbeddingRepository] talks to this interface and no longer
 * carries provider/model details through the pipeline: a backend is either a remote
 * [ProviderEmbeddingBackend] (existing `Provider.generateEmbedding()` path) or the local
 * [QwenEmbeddingBackend] (shared [QwenEmbedder] session via [QwenEngineRegistry]).
 */
interface EmbeddingBackend {
    suspend fun embed(text: String): EmbeddingResult?

    suspend fun embedBatch(texts: List<String>): List<EmbeddingResult?>
}

sealed interface RagEmbeddingSource {
    data class Provider(
        val providerSetting: ProviderSetting,
        val model: Model,
    ) : RagEmbeddingSource

    data class LocalQwen(
        val modelDir: File,
    ) : RagEmbeddingSource
}

/** Sentinel value of [Settings.ragEmbeddingModel] meaning "no provider model assigned yet". */
const val DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small"

/**
 * Pure selection of the active RAG embedding source. Local Qwen wins when its bundle is
 * ready and the user has not explicitly assigned a provider embedding model. Otherwise a
 * provider model whose id matches [Settings.ragEmbeddingModel] is used. Returns null when
 * nothing is available so callers degrade gracefully instead of touching the native runtime.
 */
fun resolveRagEmbeddingSource(
    settings: Settings,
    embedderDir: File,
    localReady: Boolean,
): RagEmbeddingSource? {
    if (localReady && settings.ragEmbeddingModel == DEFAULT_EMBEDDING_MODEL) {
        return RagEmbeddingSource.LocalQwen(embedderDir)
    }
    for (provider in settings.providers) {
        val model = provider.models.firstOrNull {
            it.modelId == settings.ragEmbeddingModel && it.type == ModelType.EMBEDDING
        } ?: continue
        return RagEmbeddingSource.Provider(provider, model)
    }
    return null
}

class ProviderEmbeddingBackend(
    private val textEmbedder: TextEmbedder,
    private val providerSetting: ProviderSetting,
    private val model: Model,
) : EmbeddingBackend {
    override suspend fun embed(text: String): EmbeddingResult? =
        textEmbedder.embed(text, providerSetting, model)

    override suspend fun embedBatch(texts: List<String>): List<EmbeddingResult?> =
        textEmbedder.embedBatch(texts, providerSetting, model)
}

class QwenEmbeddingBackend(
    private val modelDir: File,
) : EmbeddingBackend {
    private val engine: QwenEmbedder? = QwenEngineRegistry.embedder(modelDir)

    override suspend fun embed(text: String): EmbeddingResult? {
        val current = engine ?: return null
        return runCatching {
            EmbeddingResult(
                embedding = current.embed(text),
                model = "local:qwen3-embedding-0.6b",
                tokenUsage = 0,
            )
        }.getOrNull()
    }

    override suspend fun embedBatch(texts: List<String>): List<EmbeddingResult?> {
        val current = engine ?: return texts.map { null }
        return runCatching {
            current.embedBatch(texts).map {
                EmbeddingResult(it, "local:qwen3-embedding-0.6b", 0)
            }
        }.getOrNull() ?: texts.map { null }
    }
}
