package me.rerere.rikkahub.data.rag

import kotlinx.serialization.json.Json
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting

class TextEmbedder(
    val providerManager: ProviderManager,
    private val json: Json,
)

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