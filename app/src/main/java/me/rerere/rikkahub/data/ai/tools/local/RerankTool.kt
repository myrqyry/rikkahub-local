package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.reranker.QwenReranker

fun rerankTool(reranker: QwenReranker?): Tool = Tool(
    name = "rerank",
    description = """
        Rerank a list of documents against a query using the local Qwen3-Reranker-0.6B model.
        Pass the query string and a list of document strings; returns documents sorted by relevance (highest first) with scores in [0..1].
        Useful for refining search results, filtering retrieved passages, or picking the best-matching document from a set.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "The query to score documents against")
                })
                put("documents", buildJsonObject {
                    put("type", "array")
                    put("description", "List of document strings to rank")
                    put("items", buildJsonObject { put("type", "string") })
                })
                put("instruction", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional instruction for the reranker (default: 'Given a web search query, retrieve relevant passages that answer the query')")
                })
            },
            required = listOf("query", "documents"),
        )
    },
    execute = {
        val r = reranker ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
            put("error", "reranker model not available; download Qwen3-Reranker-0.6B-LiteRT files to models/reranker/")
        }.toString()))
        val params = it.jsonObject
        val query = params["query"]?.jsonPrimitive?.contentOrNull
            ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "missing required parameter: query")
            }.toString()))
        val docs = params["documents"]?.jsonArray?.mapNotNull { elem ->
                when (elem) {
                    is JsonPrimitive -> elem.contentOrNull
                    else -> null
                }
            }
            ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "missing required parameter: documents")
            }.toString()))
        val instruction = params["instruction"]?.jsonPrimitive?.contentOrNull

        val ranked = if (instruction != null) {
            r.rerank(query, docs, instruction)
        } else {
            r.rerank(query, docs)
        }
        val results = buildJsonArray {
            ranked.forEach { (doc, score) ->
                add(buildJsonObject {
                    put("document", JsonPrimitive(doc))
                    put("score", JsonPrimitive(score))
                })
            }
        }
        val payload = buildJsonObject { put("results", results) }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)