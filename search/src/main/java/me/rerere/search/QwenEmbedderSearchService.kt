package me.rerere.search

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.reranker.QwenEngineRegistry
import java.io.File

object QwenEmbedderSearchService : SearchService<SearchServiceOptions.QwenEmbedderOptions> {
    override val name: String = "Qwen Embedder"

    @Composable
    override fun Description() {}

    override fun parameters(options: SearchServiceOptions.QwenEmbedderOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search query")
                })
            },
            required = listOf("query"),
        )

    override fun scrapingParameters(options: SearchServiceOptions.QwenEmbedderOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.QwenEmbedderOptions,
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val embedder = QwenEngineRegistry.embedder(File(serviceOptions.modelDir))
                ?: error("Embedding model not installed")
            val qEmb = embedder.embed(query)
            val docs = serviceOptions.documents
            val scored = docs.mapIndexed { i, doc ->
                val dEmb = embedder.embed(doc)
                var dot = 0f
                for (j in qEmb.indices) dot += qEmb[j] * dEmb[j]
                i to dot
            }.sortedByDescending { it.second }
                .take(commonOptions.resultSize)
            val items = scored.map { (i, score) ->
                val doc = docs[i]
                SearchResult.SearchResultItem(
                    title = doc.take(80),
                    url = "",
                    text = "score: ${"%.4f".format(score)}\n$doc",
                )
            }
            SearchResult(items = items)
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.QwenEmbedderOptions,
    ): Result<ScrapedResult> = Result.success(ScrapedResult(emptyList()))
}