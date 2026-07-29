package me.rerere.rikkahub.skills.plugins

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * Adapts [PluginToolDef] entries from a plugin manifest into the [Tool] data
 * class used by the AI agent execution engine.
 *
 * Naming convention: `{pluginName}_{toolName}` to avoid collisions when
 * multiple plugins are loaded simultaneously.
 */
class PluginToolAdapter {

    /**
     * Convert a [PluginToolDef] into a [Tool] instance.
     *
     * @param pluginToolDef The tool definition from the plugin manifest.
     * @param pluginName    Used as a namespace prefix for the tool name.
     * @param execute       The suspend lambda that implements the tool's logic.
     * @return A [Tool] ready for registration with the AI engine.
     */
    fun adapt(
        pluginToolDef: PluginToolDef,
        pluginName: String,
        execute: suspend (JsonElement) -> List<UIMessagePart>,
    ): Tool {
        val inputSchema = pluginToolDef.inputSchema

        return Tool(
            name = "${pluginName}_${pluginToolDef.name}",
            description = pluginToolDef.description,
            parameters = {
                InputSchema.Obj(
                    properties = inputSchema["properties"]?.jsonObject ?: buildJsonObject {},
                    required = inputSchema["required"]?.jsonArray?.map {
                        it.jsonPrimitive.content
                    },
                )
            },
            execute = execute,
        )
    }
}