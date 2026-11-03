package me.rerere.rikkahub.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.rag.EmbeddingBackend
import me.rerere.rikkahub.data.rag.EmbeddingRepository
import me.rerere.rikkahub.data.rag.EmbeddingResult
import me.rerere.rikkahub.data.rag.VectorDao
import me.rerere.rikkahub.data.rag.VectorEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Constructed database only: does not read/write the phone's live RAG store. */
@RunWith(AndroidJUnit4::class)
class RagIndexHydrationDeviceTest {
    private class MemoryDao : VectorDao {
        val records = linkedMapOf<String, VectorEntity>()
        override suspend fun getAll() = records.values.toList()
        override suspend fun insert(entity: VectorEntity) { records[entity.id] = entity }
        override suspend fun deleteById(id: String) { records.remove(id) }
        override suspend fun deleteAll() { records.clear() }
        override suspend fun count() = records.size
    }

    private class FakeBackend(override val embeddingSpaceId: String) : EmbeddingBackend {
        var queryCalls = 0
        override suspend fun embed(text: String) =
            EmbeddingResult(floatArrayOf(1f, 0f, 0f), embeddingSpaceId, 0)
        override suspend fun embedQuery(text: String): EmbeddingResult {
            queryCalls++
            return EmbeddingResult(floatArrayOf(1f, 0f, 0f), embeddingSpaceId, 0)
        }
        override suspend fun embedBatch(texts: List<String>): List<EmbeddingResult?> =
            texts.map { embed(it) }
    }

    @Test fun reloadsPersistedVectorsAndIsolatesModelChanges() = runBlocking {
        val dao = MemoryDao()
        val a = FakeBackend("model-a")
        val b = FakeBackend("model-b")
        var active: EmbeddingBackend = a
        val repository = EmbeddingRepository({ active }, dao, Json)
        val meta = buildJsonObject { put("text", "A remembered description") }
        assertTrue(repository.indexDocument("a1", "document", meta))
        val original = repository.searchSimilar("lookup")
        assertEquals(listOf("a1"), original.map { it.id })
        assertEquals(1, a.queryCalls)

        active = b
        assertEquals(emptyList<String>(), repository.searchSimilar("lookup").map { it.id })
        assertTrue(repository.indexDocument("b1", "another document", meta))
        assertEquals(listOf("b1"), repository.searchSimilar("lookup").map { it.id })
        assertEquals(2, b.queryCalls)

        // Return to original backend, hydrate the database rather than reuse
        // the previous backend's cached embedding vectors.
        active = a
        assertEquals(listOf("a1"), repository.searchSimilar("lookup").map { it.id })
        assertEquals(2, a.queryCalls)
    }

    @Test fun loadsExistingIndexOnFirstSearchAfterRepositoryConstruction() = runBlocking {
        val dao = MemoryDao()
        val initialBackend = FakeBackend("space-persisted")
        val first = EmbeddingRepository({ initialBackend }, dao, Json)
        assertTrue(first.indexDocument("saved", "text", buildJsonObject { put("text", "persisted") }))
        val restarted = EmbeddingRepository({ initialBackend }, dao, Json)
        assertEquals("saved", restarted.searchSimilar("search").single().id)
    }
}
