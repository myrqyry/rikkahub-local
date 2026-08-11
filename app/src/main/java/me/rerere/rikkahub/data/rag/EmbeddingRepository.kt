package me.rerere.rikkahub.data.rag

import java.nio.ByteBuffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * RAG vector store. The embedding strategy is injected as a [EmbeddingBackend] provider
 * resolved per call, so switching between a local Qwen model and a cloud provider model
 * takes effect without callers carrying [me.rerere.ai.provider.ProviderSetting]/[me.rerere.ai.provider.Model]
 * through the pipeline.
 */
class EmbeddingRepository(
    private val backendProvider: suspend () -> EmbeddingBackend?,
    private val vectorDao: VectorDao,
    private val json: Json,
) {
    private val searchEngine = LocalVectorSearchEngine()

    suspend fun indexDocument(id: String, text: String, metadata: JsonObject): Boolean {
        val backend = backendProvider() ?: return false
        val result = backend.embed(text) ?: return false
        searchEngine.addVector(id, result.embedding, metadata)
        val entity = VectorEntity(
            id = id,
            embedding = floatArrayToBytes(result.embedding),
            metadata = json.encodeToString(JsonObject.serializer(), metadata),
        )
        vectorDao.insert(entity)
        return true
    }

    suspend fun searchSimilar(query: String, topK: Int = 10): List<LocalVectorSearchEngine.ScoredMatch> {
        val backend = backendProvider() ?: return emptyList()
        val result = backend.embed(query) ?: return emptyList()
        return searchEngine.search(result.embedding, topK)
    }

    suspend fun deleteDocument(id: String) {
        searchEngine.removeVector(id)
        vectorDao.deleteById(id)
    }

    suspend fun loadFromDatabase() {
        searchEngine.clear()
        for (entity in vectorDao.getAll()) {
            searchEngine.addVector(
                id = entity.id,
                embedding = bytesToFloatArray(entity.embedding),
                metadata = json.parseToJsonElement(entity.metadata).jsonObject,
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
    }
}
