package me.rerere.rikkahub.data.rag

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RagRerankingTest {

    @Test
    fun `rerankMatches reorders by reranker score and keeps finalTopK`() {
        val candidates = listOf(
            LocalVectorSearchEngine.ScoredMatch(id = "a", score = 0.9f, metadata = buildJsonObject {}),
            LocalVectorSearchEngine.ScoredMatch(id = "b", score = 0.8f, metadata = buildJsonObject {}),
            LocalVectorSearchEngine.ScoredMatch(id = "c", score = 0.7f, metadata = buildJsonObject {}),
        )
        val rerankerScores = listOf(0.1f, 0.9f, 0.5f)

        val result = EmbeddingRepository.rerankMatches(candidates, rerankerScores, finalTopK = 2)

        assertEquals(listOf("b", "c"), result.map { it.id })
        assertEquals(listOf(0.9f, 0.5f), result.map { it.score })
    }

    @Test
    fun `rerankMatches keeps all candidates when finalTopK exceeds size`() {
        val candidates = listOf(
            LocalVectorSearchEngine.ScoredMatch(id = "a", score = 0.9f, metadata = buildJsonObject {}),
            LocalVectorSearchEngine.ScoredMatch(id = "b", score = 0.8f, metadata = buildJsonObject {}),
        )
        val result = EmbeddingRepository.rerankMatches(candidates, listOf(0.2f, 0.7f), finalTopK = 10)
        assertEquals(listOf("b", "a"), result.map { it.id })
    }

    @Test
    fun `searchSimilar falls back to cosine order when no reranker`() = runBlocking {
        val backend = FixedVectorEmbeddingBackend(
            mapOf("query text" to floatArrayOf(1f, 0f), "aaa" to floatArrayOf(1f, 0f), "bbb" to floatArrayOf(0f, 1f))
        )
        val repository = EmbeddingRepository(
            backendProvider = { backend },
            vectorDao = InMemoryVectorDao(),
            json = Json,
            rerankerProvider = { null },
        )

        repository.indexDocument("1", "aaa", buildJsonObject { })
        repository.indexDocument("2", "bbb", buildJsonObject { })
        val results = repository.searchSimilar("query text", topK = 2, finalTopK = 1)

        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun `searchSimilar returns empty when no backend`() = runBlocking {
        val repository = EmbeddingRepository(
            backendProvider = { null },
            vectorDao = InMemoryVectorDao(),
            json = Json,
        )
        assertFalse(repository.indexDocument("1", "aaa", buildJsonObject { }))
        assertEquals(emptyList<LocalVectorSearchEngine.ScoredMatch>(), repository.searchSimilar("query", topK = 2, finalTopK = 1))
    }

    @Test
    fun `indexDocument stores metadata and searchSimilar returns matches with text`() = runBlocking {
        val repository = EmbeddingRepository(
            backendProvider = { FixedVectorEmbeddingBackend(mapOf("q" to floatArrayOf(1f, 0f), "doc" to floatArrayOf(1f, 0f))) },
            vectorDao = InMemoryVectorDao(),
            json = Json,
            rerankerProvider = { null },
        )
        repository.indexDocument("1", "doc", buildJsonObject { put("text", "doc") })

        val results = repository.searchSimilar("q", topK = 5, finalTopK = 5)
        assertEquals(1, results.size)
        assertEquals("doc", results[0].metadata["text"]?.jsonPrimitive?.content)
    }
}

private class FixedVectorEmbeddingBackend(
    private val vectorsByText: Map<String, FloatArray>,
) : EmbeddingBackend {
    override val embeddingSpaceId: String = "test:fixed"

    override suspend fun embed(text: String): EmbeddingResult? =
        vectorsByText[text]?.let { EmbeddingResult(it, "fake", 0) }

    override suspend fun embedBatch(texts: List<String>): List<EmbeddingResult?> =
        texts.map { embed(it) }
}

private class InMemoryVectorDao : VectorDao {
    private val store = mutableListOf<VectorEntity>()
    override suspend fun getAll(): List<VectorEntity> = store.toList()
    override suspend fun insert(entity: VectorEntity) { store.add(entity) }
    override suspend fun deleteById(id: String) { store.removeAll { it.id == id } }
    override suspend fun deleteAll() { store.clear() }
    override suspend fun count(): Int = store.size
}
