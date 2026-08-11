package me.rerere.rikkahub.data.rag

import kotlinx.serialization.json.Json
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting

data class EmbeddingResult(
    val embedding: FloatArray,
    val model: String,
    val tokenUsage: Int,
)

class TextEmbedder(
    val providerManager: ProviderManager,
    private val json: Json,
) {
    // RAG embeddings route through EmbeddingBackend (see EmbeddingBackend.kt); the local
    // Qwen path is resolved there, not here. This class only wraps cloud provider models.
}

suspend fun TextEmbedder.embed(
    text: String,
    providerSetting: ProviderSetting,
    model: Model,
): EmbeddingResult? {
    if (model.type != ModelType.EMBEDDING) return null
    val provider = providerManager.getProviderByType(providerSetting)
    val result = provider.generateEmbedding(
        providerSetting = providerSetting,
        params = EmbeddingGenerationParams(model = model, input = listOf(text)),
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
    if (model.type != ModelType.EMBEDDING) return texts.map { null }
    val provider = providerManager.getProviderByType(providerSetting)
    val result = provider.generateEmbedding(
        providerSetting = providerSetting,
        params = EmbeddingGenerationParams(model = model, input = texts),
    )
    return result.embeddings.map { EmbeddingResult(it.toFloatArray(), result.model, 0) }
}
