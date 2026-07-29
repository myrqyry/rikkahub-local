package me.rerere.rikkahub.data.rag

import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import java.io.File

data class IngestionProgress(
    val totalChunks: Int,
    val processedChunks: Int,
    val currentChunk: String,
    val errors: List<String> = emptyList(),
)

class MarkdownIngestionPipeline(
    private val chunker: MarkdownDocumentChunker,
    private val embeddingRepository: EmbeddingRepository,
    private val json: Json,
) {
    suspend fun ingestFromFile(
        filePath: String,
        providerSetting: ProviderSetting,
        model: Model,
        metadata: JsonObject = buildJsonObject {},
    ): Flow<IngestionProgress> = flow {
        val file = File(filePath)
        if (!file.exists()) {
            emit(
                IngestionProgress(
                    totalChunks = 0,
                    processedChunks = 0,
                    currentChunk = "",
                    errors = listOf("File not found: $filePath"),
                )
            )
            return@flow
        }
        val text = file.readText()
        val docId = file.nameWithoutExtension
        emitAll(ingestFromText(text, docId, providerSetting, model, metadata))
    }

    suspend fun ingestFromText(
        text: String,
        docId: String,
        providerSetting: ProviderSetting,
        model: Model,
        metadata: JsonObject = buildJsonObject {},
    ): Flow<IngestionProgress> = flow {
        val chunks = chunker.chunk(text, docId)
        val totalChunks = chunks.size
        val errors = mutableListOf<String>()

        emit(
            IngestionProgress(
                totalChunks = totalChunks,
                processedChunks = 0,
                currentChunk = "Chunking complete: $totalChunks chunks",
            )
        )

        for ((index, chunk) in chunks.withIndex()) {
            emit(
                IngestionProgress(
                    totalChunks = totalChunks,
                    processedChunks = index,
                    currentChunk = "Processing chunk ${index + 1}/$totalChunks: ${chunk.id}",
                    errors = errors.toList(),
                )
            )

            try {
                val chunkMetadata = buildJsonObject {
                    put("docId", docId)
                    put("chunkId", chunk.id)
                    put("position", chunk.position)
                    put("headingPath", json.encodeToString(chunk.headingPath))
                    put("sourceMetadata", json.encodeToString(metadata))
                }
                embeddingRepository.indexDocument(
                    id = chunk.id,
                    text = chunk.text,
                    metadata = chunkMetadata,
                    providerSetting = providerSetting,
                    model = model,
                )
            } catch (e: Exception) {
                errors.add("Failed to process chunk ${chunk.id}: ${e.message}")
            }
        }

        emit(
            IngestionProgress(
                totalChunks = totalChunks,
                processedChunks = totalChunks,
                currentChunk = "Ingestion complete",
                errors = errors.toList(),
            )
        )
    }
}