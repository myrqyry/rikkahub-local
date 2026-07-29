package me.rerere.rikkahub.data.rag

import kotlinx.serialization.json.JsonObject

class LocalVectorSearchEngine {
    private val vectors = mutableListOf<IndexEntry>()

    data class IndexEntry(
        val id: String,
        val embedding: FloatArray,
        val metadata: JsonObject,
    )

    data class ScoredMatch(
        val id: String,
        val score: Float,
        val metadata: JsonObject,
    )

    val size: Int get() = vectors.size

    fun addVector(id: String, embedding: FloatArray, metadata: JsonObject) {
        vectors.add(IndexEntry(id, embedding, metadata))
    }

    fun search(queryEmbedding: FloatArray, topK: Int = 10): List<ScoredMatch> {
        return vectors
            .map { entry ->
                ScoredMatch(
                    id = entry.id,
                    score = VectorMath.cosineSimilarity(queryEmbedding, entry.embedding),
                    metadata = entry.metadata,
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