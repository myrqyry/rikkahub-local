package me.rerere.rikkahub.data.rag

import kotlinx.serialization.json.JsonObject

class LocalVectorSearchEngine {
    private val vectors = mutableListOf<IndexEntry>()

    data class IndexEntry(
        val id: String,
        val embedding: FloatArray,
        val metadata: JsonObject,
        val embeddingSpaceId: String = "legacy",
        val embeddingDimension: Int = 0,
    )

    data class ScoredMatch(
        val id: String,
        val score: Float,
        val metadata: JsonObject,
        val embeddingSpaceId: String = "legacy",
        val embeddingDimension: Int = 0,
    )

    val size: Int get() = vectors.size

    fun addVector(
        id: String,
        embedding: FloatArray,
        metadata: JsonObject,
        embeddingSpaceId: String,
        embeddingDimension: Int,
    ) {
        vectors.add(IndexEntry(id, embedding, metadata, embeddingSpaceId, embeddingDimension))
    }

    fun search(
        queryEmbedding: FloatArray,
        embeddingSpaceId: String,
        embeddingDimension: Int,
        topK: Int = 10,
    ): List<ScoredMatch> {
        return vectors
            .filter {
                it.embeddingSpaceId == embeddingSpaceId && it.embeddingDimension == embeddingDimension
            }
            .map { entry ->
                ScoredMatch(
                    id = entry.id,
                    score = VectorMath.cosineSimilarity(queryEmbedding, entry.embedding),
                    metadata = entry.metadata,
                    embeddingSpaceId = entry.embeddingSpaceId,
                    embeddingDimension = entry.embeddingDimension,
                )
            }
            .sortedByDescending { it.score }
            .take(topK)
    }

    fun removeVector(id: String): Boolean {
        return vectors.removeAll { it.id == id }
    }

    fun clear() {
        vectors.clear()
    }
}
