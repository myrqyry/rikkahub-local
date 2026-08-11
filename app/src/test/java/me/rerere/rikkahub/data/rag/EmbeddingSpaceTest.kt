package me.rerere.rikkahub.data.rag

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingSpaceTest {
    @Test
    fun switchingEmbeddingModelsExcludesOldVectors() = runBlocking {
        val dao = SpaceVectorDao()
        val first = FixedBackend("provider:a:model", floatArrayOf(1f, 0f))
        val second = FixedBackend("local-qwen:revision-2", floatArrayOf(1f, 0f))

        EmbeddingRepository({ first }, dao, Json {}).indexDocument("a", "doc", metadata())
        val matches = EmbeddingRepository({ second }, dao, Json {}).searchSimilar("query")

        assertTrue(matches.isEmpty())
    }

    @Test
    fun matchingVectorsRemainSearchableAfterReload() = runBlocking {
        val dao = SpaceVectorDao()
        val backend = FixedBackend("local-qwen:revision-1", floatArrayOf(1f, 0f))
        EmbeddingRepository({ backend }, dao, Json {}).indexDocument("a", "doc", metadata())

        val repository = EmbeddingRepository({ backend }, dao, Json {})
        repository.loadFromDatabase()

        assertEquals(listOf("a"), repository.searchSimilar("query").map { it.id })
    }

    @Test
    fun sameDimensionModelsCannotCrossSearch() = runBlocking {
        val dao = SpaceVectorDao()
        EmbeddingRepository({ FixedBackend("provider:a:model", floatArrayOf(1f, 0f)) }, dao, Json {})
            .indexDocument("a", "doc", metadata())

        val matches = EmbeddingRepository(
            { FixedBackend("provider:b:model", floatArrayOf(1f, 0f)) },
            dao,
            Json {},
        ).searchSimilar("query")

        assertTrue(matches.isEmpty())
    }

    @Test
    fun differentDimensionsFailClosed() = runBlocking {
        val dao = SpaceVectorDao()
        EmbeddingRepository({ FixedBackend("provider:a:model", floatArrayOf(1f, 0f)) }, dao, Json {})
            .indexDocument("a", "doc", metadata())

        val matches = EmbeddingRepository(
            { FixedBackend("provider:a:model", floatArrayOf(1f, 0f, 0f)) },
            dao,
            Json {},
        ).searchSimilar("query")

        assertTrue(matches.isEmpty())
    }

    @Test
    fun newerQwenRevisionMakesOldVectorsStale() = runBlocking {
        val dao = SpaceVectorDao()
        EmbeddingRepository({ FixedBackend("local-qwen:revision-1", floatArrayOf(1f, 0f)) }, dao, Json {})
            .indexDocument("a", "doc", metadata())

        val status = EmbeddingRepository(
            { FixedBackend("local-qwen:revision-2", floatArrayOf(1f, 0f)) },
            dao,
            Json {},
        ).indexStatus()

        assertEquals(0, status.documentCount)
        assertTrue(status.stale)
    }

    private fun metadata() = buildJsonObject { put("text", "doc") }
}

private class FixedBackend(
    override val embeddingSpaceId: String,
    private val vector: FloatArray,
) : EmbeddingBackend {
    override suspend fun embed(text: String): EmbeddingResult =
        EmbeddingResult(vector.copyOf(), "test", 0)

    override suspend fun embedBatch(texts: List<String>): List<EmbeddingResult> =
        texts.map { embed(it) }
}

private class SpaceVectorDao : VectorDao {
    private val entities = mutableListOf<VectorEntity>()

    override suspend fun getAll(): List<VectorEntity> = entities.toList()

    override suspend fun insert(entity: VectorEntity) {
        entities.removeAll { it.id == entity.id }
        entities += entity
    }

    override suspend fun deleteById(id: String) {
        entities.removeAll { it.id == id }
    }

    override suspend fun deleteAll() {
        entities.clear()
    }

    override suspend fun count(): Int = entities.size
}
