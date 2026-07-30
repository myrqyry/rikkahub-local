package me.rerere.rikkahub.data.ai.compressor

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createRetrievalTool(store: CompressedContentStore): Tool = Tool(
    name = "retrieve_content",
    description = "Retrieve the full original content that was compressed with a key. Use this when a tool output was truncated and shows 'retrieve_content key=...' — pass that key here to get the complete output.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("key", buildJsonObject { put("type", "string"); put("description", "The content key from the truncated output") })
            },
            required = listOf("key"),
        )
    },
    needsApproval = { false },
    execute = {
        val key = it.jsonObject["key"]?.jsonPrimitive?.content ?: error("key required")
        val content = store.retrieve(key)
        if (content != null) listOf(UIMessagePart.Text("# Full content ($key)\n\n$content"))
        else listOf(UIMessagePart.Text("No stored content found for key: $key"))
    },
)
