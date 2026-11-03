package me.rerere.rikkahub.data.rag

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class MediaEmbeddingRepositoryTest {
    @Test fun imageAndAudioIndexingUsesRealMediaMethodsNotFakedTextEmbeddings() = runBlocking {
        val backend = TestMediaBackend()
        val repository = EmbeddingRepository({ backend }, TestMediaVectorDao(), Json {})
        assertTrue(repository.indexMedia(
            "image-1", byteArrayOf(1, 2), MediaEmbeddingKind.IMAGE,
            buildJsonObject { put("text", "Photo of a red flower"); put("mediaType", "image") },
        ))
        assertTrue(repository.indexMedia(
            "audio-1", byteArrayOf(3, 4), MediaEmbeddingKind.AUDIO,
            buildJsonObject { put("text", "Recording of birds singing"); put("mediaType", "audio") },
        ))
        assertEquals(1, backend.imageCalls)
        assertEquals(1, backend.audioCalls)
        assertEquals(0, backend.documentTextCalls)
        assertEquals(listOf("image-1", "audio-1"), repository.searchSimilar("flower", topK = 5, finalTopK = 5).map { it.id })
        assertEquals(listOf("image-1", "audio-1"), repository.searchMedia("flower").map { it.id })
    }

    @Test fun mediaSearchDoesNotGetBuriedByManyHighScoringTextChunks() = runBlocking {
        val dao = TestMediaVectorDao()
        val repo = EmbeddingRepository({ TestMediaBackend() }, dao, Json {})
        repeat(120) { index ->
            repo.indexDocument("text-$index", "similar text", buildJsonObject { put("text", "text-$index") })
        }
        repo.indexMedia("photo", byteArrayOf(2), MediaEmbeddingKind.IMAGE,
            buildJsonObject { put("text", "Photo"); put("mediaType", "image") })
        assertEquals(listOf("photo"), repo.searchMedia("similar").map { it.id })
    }

    @Test fun rejectsMediaIndexIntoAChangedEmbeddingSpace() = runBlocking {
        val dao = TestMediaVectorDao()
        val repo = EmbeddingRepository({ TestMediaBackend("space-b") }, dao, Json {})
        assertFalse(repo.indexMedia("photo", byteArrayOf(2), MediaEmbeddingKind.IMAGE,
            buildJsonObject { put("mediaType", "image") }, expectedSpaceId = "space-a"))
        assertTrue(dao.getAll().isEmpty())
    }

    @Test fun backendWithoutMediaSupportNeverMislabelsTextEmbeddingAsMedia() = runBlocking {
        val dao = TestMediaVectorDao()
        val textOnly = object : EmbeddingBackend {
            override val embeddingSpaceId = "plain-text"
            override suspend fun embed(text: String) = EmbeddingResult(floatArrayOf(1f, 0f), "plain", 0)
            override suspend fun embedBatch(texts: List<String>) = texts.map { embed(it) }
        }
        val repo = EmbeddingRepository({ textOnly }, dao, Json {})
        assertFalse(repo.indexMedia("unsupported", byteArrayOf(1), MediaEmbeddingKind.IMAGE, buildJsonObject {}))
        assertTrue(dao.getAll().isEmpty())
    }

    @Test fun selectingDifferentVariantNeverSearchesOldMediaVectors() = runBlocking {
        val dao = TestMediaVectorDao()
        val vision = TestMediaBackend("embedding:vision")
        val full = TestMediaBackend("embedding:full")
        assertTrue(EmbeddingRepository({ vision }, dao, Json {}).indexMedia(
            "photo", byteArrayOf(7), MediaEmbeddingKind.IMAGE, buildJsonObject { put("text", "Photo") },
        ))
        assertTrue(EmbeddingRepository({ full }, dao, Json {}).searchSimilar("photo").isEmpty())
    }
}

private class TestMediaBackend(override val embeddingSpaceId: String = "embedding:vision") : MediaEmbeddingBackend {
    var imageCalls = 0
    var audioCalls = 0
    var documentTextCalls = 0
    override suspend fun embed(text: String): EmbeddingResult {
        documentTextCalls++
        return EmbeddingResult(floatArrayOf(1f, 0f), "test", 0)
    }
    override suspend fun embedQuery(text: String) = EmbeddingResult(floatArrayOf(1f, 0f), "test", 0)
    override suspend fun embedBatch(texts: List<String>) = texts.map { embed(it) }
    override suspend fun embedImage(imageBytes: ByteArray): EmbeddingResult {
        imageCalls++
        return EmbeddingResult(floatArrayOf(1f, 0f), "test", 0)
    }
    override suspend fun embedAudio(audioBytes: ByteArray): EmbeddingResult {
        audioCalls++
        return EmbeddingResult(floatArrayOf(0f, 1f), "test", 0)
    }
}

private class TestMediaVectorDao : VectorDao {
    private val rows = linkedMapOf<String, VectorEntity>()
    override suspend fun getAll() = rows.values.toList()
    override suspend fun insert(entity: VectorEntity) { rows[entity.id] = entity }
    override suspend fun deleteById(id: String) { rows.remove(id) }
    override suspend fun deleteAll() { rows.clear() }
    override suspend fun count() = rows.size
}
