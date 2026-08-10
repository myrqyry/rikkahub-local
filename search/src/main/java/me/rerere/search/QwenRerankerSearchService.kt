package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.reranker.QwenReranker
import java.io.File

object QwenRerankerSearchService : SearchService<SearchServiceOptions.QwenRerankerOptions> {
    override val name: String = "Qwen Reranker"

    @Composable
    override fun Description() {}

    override fun parameters(options: SearchServiceOptions.QwenRerankerOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search query")
                })
            },
            required = listOf("query"),
        )

    override fun scrapingParameters(options: SearchServiceOptions.QwenRerankerOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.QwenRerankerOptions,
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val reranker = QwenReranker(File(serviceOptions.modelDir))
            reranker.use { r ->
                val items = r.rerank(query, serviceOptions.documents, serviceOptions.instruction)
                    .take(commonOptions.resultSize)
                    .map { (doc, score) ->
                        SearchResult.SearchResultItem(
                            title = doc.take(80),
                            url = "",
                            text = "score: ${"%.4f".format(score)}\n$doc",
                        )
                    }
                SearchResult(items = items)
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.QwenRerankerOptions,
    ): Result<ScrapedResult> = Result.success(ScrapedResult(emptyList()))
}