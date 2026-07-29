package me.rerere.rikkahub.data.rag

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import java.nio.ByteBuffer

class EmbeddingRepository(
    private val textEmbedder: TextEmbedder,
    private val vectorDao: VectorDao,
    private val json: Json,
) {
    private val searchEngine = LocalVectorSearchEngine()

    suspend fun indexDocument(
        id: String,
        text: String,
        metadata: JsonObject,
        providerSetting: ProviderSetting,
        model: Model,
    ): Boolean {
        val result = textEmbedder.embed(text, providerSetting, model) ?: return false
        searchEngine.addVector(id, result.embedding, metadata)
        val entity = VectorEntity(
            id = id,
            embedding = floatArrayToBytes(result.embedding),
            metadata = json.encodeToString(JsonObject.serializer(), metadata),
        )
        vectorDao.insert(entity)
        return true
    }

    suspend fun searchSimilar(
        query: String,
        topK: Int = 10,
        providerSetting: ProviderSetting,
        model: Model,
    ): List<LocalVectorSearchEngine.ScoredMatch> {
        val result = textEmbedder.embed(query, providerSetting, model) ?: return emptyList()
        return searchEngine.search(result.embedding, topK)
    }

    suspend fun deleteDocument(id: String) {
        searchEngine.removeVector(id)
        vectorDao.deleteById(id)
    }

    suspend fun loadFromDatabase() {
        searchEngine.clear()
        val entities = vectorDao.getAll()
        for (entity in entities) {
            val metadata = json.parseToJsonElement(entity.metadata).jsonObject
            searchEngine.addVector(
                id = entity.id,
                embedding = bytesToFloatArray(entity.embedding),
                metadata = metadata,
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