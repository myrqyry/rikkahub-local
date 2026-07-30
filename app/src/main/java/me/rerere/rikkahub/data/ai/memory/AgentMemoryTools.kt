package me.rerere.rikkahub.data.ai.memory

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAgentMemoryTools(memory: AgentMemoryManager): List<Tool> = listOf(
    Tool(
        name = "store_memory",
        description = "Store a piece of information, preference, decision, or observation the assistant learned during this conversation. Use this to remember facts about the user, their preferences, project decisions, or anything worth recalling later. Supply type as 'preference', 'decision', 'fact', 'note', 'reference', or 'custom'.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("type", buildJsonObject { put("type", "string"); put("description", "Memory category: preference/decision/fact/note/reference/custom") })
                    put("content", buildJsonObject { put("type", "string"); put("description", "The information to remember") })
                    put("tags", buildJsonObject { put("type", "string"); put("description", "Optional comma-separated tags") })
                },
                required = listOf("type", "content"),
            )
        },
        needsApproval = { false },
        execute = {
            val obj = it.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: error("type required")
            val content = obj["content"]?.jsonPrimitive?.content ?: error("content required")
            val tags = (obj["tags"]?.jsonPrimitive?.content)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            val entry = memory.store(type, content, tags)
            listOf(UIMessagePart.Text("Stored (${entry.id.take(8)}): $type — ${content.take(80)}"))
        },
    ),
    Tool(
        name = "search_memory",
        description = "Search stored memories by keyword. Returns recent matches sorted by recency. Use this before asking the user for information you might have already stored.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", buildJsonObject { put("type", "string"); put("description", "Keyword to search for in memory contents") })
                    put("limit", buildJsonObject { put("type", "number"); put("description", "Max results (default 10)") })
                },
                required = listOf("query"),
            )
        },
        needsApproval = { false },
        execute = {
            val obj = it.jsonObject
            val query = obj["query"]?.jsonPrimitive?.content ?: error("query required")
            val limit = (obj["limit"]?.jsonPrimitive?.content)?.toIntOrNull() ?: 10
            val results = memory.search(query, limit)
            if (results.isEmpty()) return@Tool listOf(UIMessagePart.Text("No memories found for \"$query\"."))
            val text = results.joinToString("\n\n") { e ->
                "[${e.type}] ${e.content}\n  tags: ${e.tags.joinToString(", ")}  ·  ${e.id.take(8)}"
            }
            listOf(UIMessagePart.Text("$query — ${results.size} memory(ies):\n\n$text"))
        },
    ),
)
