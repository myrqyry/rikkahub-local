package me.rerere.rikkahub.data.rag

import java.nio.ByteBuffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.reranker.QwenReranker

data class EmbeddingIndexStatus(
    val documentCount: Int,
    val stale: Boolean,
)

/**
 * RAG vector store. The embedding strategy is injected as a [EmbeddingBackend] provider
 * resolved per call, so switching between a local Qwen model and a cloud provider model
 * takes effect without callers carrying [me.rerere.ai.provider.ProviderSetting]/[me.rerere.ai.provider.Model]
 * through the pipeline. When a local [QwenReranker] is available and the indexed chunks
 * carry their source text in metadata, [searchSimilar] re-ranks the top cosine matches
 * and returns the best [finalTopK] in reranker order.
 */
class EmbeddingRepository(
    private val backendProvider: suspend () -> EmbeddingBackend?,
    private val vectorDao: VectorDao,
    private val json: Json,
    private val rerankerProvider: () -> QwenReranker? = { null },
) {
    private val searchEngine = LocalVectorSearchEngine()

    suspend fun indexDocument(id: String, text: String, metadata: JsonObject): Boolean {
        val backend = backendProvider() ?: return false
        val result = backend.embed(text) ?: return false
        val embeddingDimension = result.embedding.size
        searchEngine.addVector(
            id = id,
            embedding = result.embedding,
            metadata = metadata,
            embeddingSpaceId = backend.embeddingSpaceId,
            embeddingDimension = embeddingDimension,
        )
        val entity = VectorEntity(
            id = id,
            embedding = floatArrayToBytes(result.embedding),
            metadata = json.encodeToString(JsonObject.serializer(), metadata),
            embeddingSpaceId = backend.embeddingSpaceId,
            embeddingDimension = embeddingDimension,
        )
        vectorDao.insert(entity)
        return true
    }

    suspend fun searchSimilar(
        query: String,
        topK: Int = 20,
        finalTopK: Int = 5,
    ): List<LocalVectorSearchEngine.ScoredMatch> {
        val backend = backendProvider() ?: return emptyList()
        val result = backend.embed(query) ?: return emptyList()
        val candidates = searchEngine.search(
            queryEmbedding = result.embedding,
            embeddingSpaceId = backend.embeddingSpaceId,
            embeddingDimension = result.embedding.size,
            topK = topK,
        )
        if (candidates.isEmpty()) return emptyList()

        val reranker = rerankerProvider()
        if (reranker == null) return candidates.take(finalTopK)

        val texts = candidates.map { it.metadata["text"]?.jsonPrimitive?.content }
        if (texts.any { it == null }) {
            // Rows indexed before chunk text was stored: reranking cannot run, keep cosine order.
            return candidates.take(finalTopK)
        }
        @Suppress("UNCHECKED_CAST")
        val scores = reranker.score(query, texts as List<String>)
        return rerankMatches(candidates, scores, finalTopK)
    }

    suspend fun indexStatus(): EmbeddingIndexStatus {
        val rows = vectorDao.getAll()
        if (rows.isEmpty()) return EmbeddingIndexStatus(documentCount = 0, stale = false)

    val backend = backendProvider()
        ?: return EmbeddingIndexStatus(documentCount = 0, stale = true)
        val currentSpace = backend.embeddingSpaceId
        val matchingRows = rows.filter { it.embeddingSpaceId == currentSpace }
        val dimensions = matchingRows.map { it.embeddingDimension }.toSet()
        val stale = matchingRows.any { it.embeddingDimension <= 0 } ||
            dimensions.size > 1 ||
            matchingRows.size != rows.size
    return EmbeddingIndexStatus(
        documentCount = if (stale) 0 else matchingRows.size,
        stale = stale,
    )
}

    suspend fun deleteDocument(id: String) {
        searchEngine.removeVector(id)
        vectorDao.deleteById(id)
    }

    suspend fun loadFromDatabase() {
        searchEngine.clear()
        val backend = backendProvider() ?: return
        for (entity in vectorDao.getAll()) {
            if (entity.embeddingSpaceId != backend.embeddingSpaceId ||
                entity.embeddingDimension <= 0
            ) continue
            searchEngine.addVector(
                id = entity.id,
                embedding = bytesToFloatArray(entity.embedding),
                metadata = json.parseToJsonElement(entity.metadata).jsonObject,
                embeddingSpaceId = entity.embeddingSpaceId,
                embeddingDimension = entity.embeddingDimension,
            )
        }
    }

    companion object {
        fun floatArrayToBytes(array: FloatArray): ByteArray {
            val buffer = ByteBuffer.allocate(array.size * 4)
            buffer.asFloatBuffer().put(array)
            return buffer.array()
        }

        fun bytesToFloatArray(bytes: ByteArray): FloatArray {
            val buffer = ByteBuffer.wrap(bytes)
            val result = FloatArray(bytes.size / 4)
            buffer.asFloatBuffer().get(result)
            return result
        }

        /**
         * Reorders the top cosine matches by the given reranker scores (higher first) and
         * keeps the best [finalTopK]. Pure so the ordering rule is unit-testable without a
         * native GPU reranker instance.
         */
        internal fun rerankMatches(
            candidates: List<LocalVectorSearchEngine.ScoredMatch>,
            scores: List<Float>,
            finalTopK: Int,
        ): List<LocalVectorSearchEngine.ScoredMatch> =
            candidates.zip(scores)
                .sortedByDescending { (_, score) -> score }
                .take(finalTopK)
                .map { (match, score) -> match.copy(score = score) }
    }
}
