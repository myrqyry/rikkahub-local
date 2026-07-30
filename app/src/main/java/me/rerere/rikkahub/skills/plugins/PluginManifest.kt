package me.rerere.rikkahub.skills.plugins

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Deserialized Claude plugin manifest (plugin.json).
 *
 * Field naming follows the Claude plugin manifest spec so that the default
 * Json (ignoreUnknownKeys = true) can parse straight from the file.
 */
@Serializable
data class PluginManifest(
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val commands: List<PluginCommand> = emptyList(),
    val hooks: List<PluginHook> = emptyList(),
    val tools: List<PluginToolDef> = emptyList(),
    val permissions: List<String> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parse a [plugin.json] string into [PluginManifest].
         * Returns [Result.failure] for malformed JSON or missing required fields.
         */
        fun parse(jsonString: String): Result<PluginManifest> = runCatching {
            json.decodeFromString<PluginManifest>(jsonString)
        }
    }
}

@Serializable
data class PluginCommand(
    val name: String,
    val description: String,
    val args: List<PluginCommandArg> = emptyList(),
)

@Serializable
data class PluginCommandArg(
    val name: String,
    val description: String,
    val required: Boolean = false,
)

@Serializable
data class PluginHook(
    val event: String,
    val handler: String,
)

@Serializable
data class PluginToolDef(
    val name: String,
    val description: String,
    @SerialName("input_schema")
    val inputSchema: JsonObject,
    @SerialName("x-rikkahub-command")
    val rikkahubCommand: String? = null,
)