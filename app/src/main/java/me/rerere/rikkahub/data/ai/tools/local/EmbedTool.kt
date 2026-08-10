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
import me.rerere.reranker.QwenEmbedder

fun embedTextTool(embedder: QwenEmbedder?): Tool = Tool(
    name = "embed_text",
    description = "Generate a 1024-dimensional text embedding using the local Qwen3-Embedding-0.6B model. Useful for semantic comparison, clustering, or as input to other ranking tools.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "The text to embed")
                })
            },
            required = listOf("text"),
        )
    },
    execute = {
        val e = embedder ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
            put("error", "embedding model not available; download Qwen3-Embedding-0.6B-LiteRT files to models/embedder/")
        }.toString()))
        val text = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull
            ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "missing required parameter: text")
            }.toString()))
        val emb = e.embed(text)
        val payload = buildJsonObject {
            put("embedding", buildJsonArray { emb.forEach { add(JsonPrimitive(it)) } })
            put("dimensions", JsonPrimitive(emb.size))
        }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)

fun compareTextsTool(embedder: QwenEmbedder?): Tool = Tool(
    name = "compare_texts",
    description = "Compare two texts semantically using cosine similarity on local Qwen3-Embedding-0.6B embeddings. Returns a score in [0..1] where 1 means identical meaning.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("text1", buildJsonObject {
                    put("type", "string")
                    put("description", "First text to compare")
                })
                put("text2", buildJsonObject {
                    put("type", "string")
                    put("description", "Second text to compare")
                })
            },
            required = listOf("text1", "text2"),
        )
    },
    execute = {
        val e = embedder ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
            put("error", "embedding model not available")
        }.toString()))
        val params = it.jsonObject
        val t1 = params["text1"]?.jsonPrimitive?.contentOrNull
            ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "missing required parameter: text1")
            }.toString()))
        val t2 = params["text2"]?.jsonPrimitive?.contentOrNull
            ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "missing required parameter: text2")
            }.toString()))
        val e1 = e.embed(t1)
        val e2 = e.embed(t2)
        var dot = 0f
        for (i in e1.indices) dot += e1[i] * e2[i]
        val payload = buildJsonObject { put("similarity", JsonPrimitive(dot)) }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)