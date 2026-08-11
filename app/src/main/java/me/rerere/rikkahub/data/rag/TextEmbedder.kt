package me.rerere.rikkahub.data.rag

import kotlinx.serialization.json.Json
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.reranker.QwenEmbedder

class TextEmbedder(
    val providerManager: ProviderManager,
    private val json: Json,
    private val localEmbedder: () -> QwenEmbedder? = { null },
) {
    @Volatile
    private var cachedEmbedder: QwenEmbedder? = null

    internal suspend fun resolveLocalEmbedder(): QwenEmbedder? {
        cachedEmbedder?.let { return it }
        val engine = runCatching { localEmbedder() }.getOrNull()
        if (engine != null) cachedEmbedder = engine
        return engine
    }

    internal suspend fun embedLocal(text: String): EmbeddingResult? {
        val engine = resolveLocalEmbedder() ?: return null
        return runCatching {
            EmbeddingResult(
                embedding = engine.embed(text),
                model = "local:qwen3-embedding-0.6b",
                tokenUsage = 0,
            )
        }.getOrNull()
    }
}

data class EmbeddingResult(
    val embedding: FloatArray,
    val model: String,
    val tokenUsage: Int,
)

suspend fun TextEmbedder.embed(
    text: String,
    providerSetting: ProviderSetting,
    model: Model,
): EmbeddingResult? {
    embedLocal(text)?.let { return it }
    if (model.type != ModelType.EMBEDDING) return null
    val provider = providerManager.getProviderByType(providerSetting)
    val result = provider.generateEmbedding(
        providerSetting = providerSetting,
        params = EmbeddingGenerationParams(
            model = model,
            input = listOf(text),
        ),
    )
    return EmbeddingResult(
        embedding = result.embeddings.firstOrNull()?.toFloatArray() ?: return null,
        model = result.model,
        tokenUsage = 0,
    )
}

suspend fun TextEmbedder.embedBatch(
    texts: List<String>,
    providerSetting: ProviderSetting,
    model: Model,
): List<EmbeddingResult?> {
    val engine = resolveLocalEmbedder()
    if (engine != null) {
        return runCatching {
            engine.embedBatch(texts).map { vector ->
                EmbeddingResult(
                    embedding = vector,
                    model = "local:qwen3-embedding-0.6b",
                    tokenUsage = 0,
                )
            }
        }.getOrNull() ?: texts.map { null }
    }
    if (model.type != ModelType.EMBEDDING) {
        return texts.map { null }
    }
    val provider = providerManager.getProviderByType(providerSetting)
    val result = provider.generateEmbedding(
        providerSetting = providerSetting,
        params = EmbeddingGenerationParams(
            model = model,
            input = texts,
        ),
    )
    return result.embeddings.map { floats ->
        EmbeddingResult(
            embedding = floats.toFloatArray(),
            model = result.model,
            tokenUsage = 0,
        )
    }
}
