package me.rerere.rikkahub.data.modelregistry

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderSetting
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A provider-scoped view of the public models.dev API. This does not prove that a user's API key
 * can access the model or that every advertised feature works on the selected provider.
 */
class ModelsDevModelCatalog private constructor(
    private val providers: Map<String, CatalogProvider>,
) {
    private data class CatalogProvider(
        val endpoint: String?,
        val npm: String?,
        val models: Map<String, ModelsDevModelMetadata>,
    )

    companion object {
        /** Reuse the public directory cache; only retain the requested provider's models. */
        fun loadForProvider(cacheFile: File, provider: ProviderSetting): ModelsDevModelCatalog {
            ModelsDevProviderDirectory.load(cacheFile)
            return parseForProvider(cacheFile.readText(), provider)
        }

        fun parseForProvider(json: String, provider: ProviderSetting): ModelsDevModelCatalog {
            val root = Json.parseToJsonElement(json) as? JsonObject
                ?: throw IllegalArgumentException("Expected models.dev catalog object")
            val providers = root.mapNotNull { (id, value) ->
                val entry = value as? JsonObject ?: return@mapNotNull null
                if (entry["id"]?.jsonPrimitive?.contentOrNull?.let { it != id } == true) return@mapNotNull null
                id to CatalogProvider(
                    endpoint = entry["api"]?.jsonPrimitive?.contentOrNull,
                    npm = entry["npm"]?.jsonPrimitive?.contentOrNull,
                    models = emptyMap(),
                )
            }.toMap()
            val id = ModelsDevModelCatalog(providers).providerId(provider)
                ?: return ModelsDevModelCatalog(emptyMap())
            val selected = root[id] ?: return ModelsDevModelCatalog(emptyMap())
            return parse(JsonObject(mapOf(id to selected)).toString())
        }

        fun parse(json: String): ModelsDevModelCatalog {
            val root = Json.parseToJsonElement(json) as? JsonObject
                ?: throw IllegalArgumentException("Expected models.dev catalog object")
            return ModelsDevModelCatalog(root.mapNotNull { (key, obj) ->
                val provider = obj as? JsonObject ?: return@mapNotNull null
                if (provider["id"]?.jsonPrimitive?.contentOrNull?.let { it != key } == true) {
                    return@mapNotNull null
                }
                val modelObjects = provider["models"] as? JsonObject ?: return@mapNotNull null
                val models = modelObjects.mapNotNull { (modelId, node) ->
                    val model = node as? JsonObject ?: return@mapNotNull null
                    if (model["id"]?.jsonPrimitive?.contentOrNull?.let { it != modelId } == true) {
                        return@mapNotNull null
                    }
                    modelId to ModelsDevModelMetadata.fromProviderModel(key, modelId, model)
                }.toMap()
                key to CatalogProvider(
                    endpoint = provider["api"]?.jsonPrimitive?.contentOrNull,
                    npm = provider["npm"]?.jsonPrimitive?.contentOrNull,
                    models = models,
                )
            }.toMap())
        }
    }

    /**
     * Only exact endpoint matches (including path) and known native provider endpoints qualify.
     * No substring/model-name guessing across hosts.
     */
    fun providerId(provider: ProviderSetting): String? {
        val endpoint = when (provider) {
            is ProviderSetting.OpenAI -> provider.baseUrl
            is ProviderSetting.Claude -> provider.baseUrl
            is ProviderSetting.Google -> if (!provider.vertexAI) provider.baseUrl else return null
            else -> return null
        }
        val normalized = canonicalEndpoint(endpoint) ?: return null
        val known = when (provider) {
            is ProviderSetting.OpenAI -> if (normalized == "https://api.openai.com/v1") "openai" else null
            is ProviderSetting.Claude -> if (normalized == "https://api.anthropic.com/v1") "anthropic" else null
            is ProviderSetting.Google -> if (normalized == "https://generativelanguage.googleapis.com/v1beta") "google" else null
            else -> null
        }
        if (known != null && providers.containsKey(known)) return known
        val matches = providers.filterValues { entry ->
            entry.endpoint?.let(::canonicalEndpoint) == normalized && when (provider) {
                is ProviderSetting.OpenAI -> entry.npm in setOf(
                    "@ai-sdk/openai-compatible", "@openrouter/ai-sdk-provider"
                )
                is ProviderSetting.Claude -> entry.npm == "@ai-sdk/anthropic"
                is ProviderSetting.Google -> entry.npm == "@ai-sdk/google"
                else -> false
            }
        }
        return matches.keys.singleOrNull()
    }

    fun find(provider: ProviderSetting, modelId: String): ModelsDevModelMetadata? =
        providerId(provider)?.let { providers[it]?.models?.get(modelId) }

    fun suggestedModels(provider: ProviderSetting): List<ModelsDevModelMetadata> =
        providerId(provider)?.let { providers[it]?.models?.values }
            ?.filter { it.isSelectableAsChatOrImage && !it.isDeprecated }
            ?.sortedBy { it.displayName.lowercase() }
            ?: emptyList()

    private fun canonicalEndpoint(raw: String): String? {
        val url = raw.trim().toHttpUrlOrNull() ?: return null
        // A loopback URL identifies a port on the *user's* device, not a public service.
        if (url.host in setOf("localhost", "127.0.0.1", "::1")) return null
        // Exact URL matching includes path/port; no query-based masquerading.
        if (url.query != null || url.fragment != null || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        return url.toString().trimEnd('/')
    }
}

data class ModelsDevModelMetadata(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val inputModalities: List<Modality>?,
    val outputModalities: List<Modality>?,
    val toolCalling: Boolean?,
    val reasoning: Boolean?,
    val structuredOutput: Boolean?,
    val temperature: Boolean?,
    val contextLength: Int?,
    val maxOutputTokens: Int?,
    val pricePromptPerToken: Double?,
    val priceCompletionPerToken: Double?,
    val reasoningEfforts: List<String>,
    val reasoningControlTypes: List<String>,
    val advertisedInputModalities: List<String>,
    val advertisedOutputModalities: List<String>,
    val isSelectableAsChatOrImage: Boolean,
    val isDeprecated: Boolean,
) {
    /** Only on a NEW model. Never silently rewrite persisted user overrides on refresh. */
    fun applyToNewModel(model: Model): Model {
        require(model.modelId == modelId) { "Wrong model ID for metadata" }
        val inMods = inputModalities ?: model.inputModalities
        val outMods = outputModalities ?: model.outputModalities
        val abilities = model.abilities.toMutableSet().apply {
            if (toolCalling == true) add(ModelAbility.TOOL)
            if (toolCalling == false) remove(ModelAbility.TOOL)
            if (reasoning == true) add(ModelAbility.REASONING)
            if (reasoning == false) remove(ModelAbility.REASONING)
        }.toList()
        val paramSet = model.supportedParameters.toMutableSet().apply {
            if (toolCalling == true) add("tools")
            if (toolCalling == false) { remove("tools"); remove("tool_choice") }
            if (reasoning == true) add("reasoning")
            if (reasoning == false) { remove("reasoning"); remove("include_reasoning") }
            if (structuredOutput == true) add("response_format")
            if (structuredOutput == false) remove("response_format")
            if (temperature == true) add("temperature")
            if (temperature == false) remove("temperature")
        }
        return model.copy(
            displayName = if (model.displayName.isBlank() || model.displayName == modelId) displayName else model.displayName,
            type = if (Modality.IMAGE in outMods) ModelType.IMAGE else model.type,
            inputModalities = inMods,
            outputModalities = outMods,
            abilities = abilities,
            contextLength = contextLength ?: model.contextLength,
            maxOutputTokens = maxOutputTokens ?: model.maxOutputTokens,
            supportedParameters = paramSet.toList(),
            pricePromptPerToken = pricePromptPerToken ?: model.pricePromptPerToken,
            priceCompletionPerToken = priceCompletionPerToken ?: model.priceCompletionPerToken,
            supportsTemperature = temperature ?: model.supportsTemperature,
            supportsStructuredOutput = structuredOutput ?: model.supportsStructuredOutput,
            reasoningEfforts = if (reasoningEfforts.isNotEmpty()) reasoningEfforts else model.reasoningEfforts,
            reasoningControlTypes = if (reasoningControlTypes.isNotEmpty()) reasoningControlTypes else model.reasoningControlTypes,
            advertisedInputModalities = advertisedInputModalities,
            advertisedOutputModalities = advertisedOutputModalities,
            metadataSource = "models.dev:$providerId/$modelId",
        )
    }

    /** Provider-reported rich model capabilities take precedence over catalog claims. */
    fun enrichProviderModel(model: Model): Model {
        if (model.supportedParameters.isEmpty() && model.metadataSource?.startsWith("provider:") != true) {
            return applyToNewModel(model)
        }
        val directOpenRouter = model.metadataSource == "provider:openrouter"
        val reportedTemperature = when {
            "temperature" in model.supportedParameters -> true
            directOpenRouter -> false
            else -> null
        }
        val reportedStructuredOutput = when {
            "response_format" in model.supportedParameters ||
                "structured_outputs" in model.supportedParameters -> true
            directOpenRouter -> false
            else -> null
        }
        return model.copy(
            contextLength = model.contextLength ?: contextLength,
            maxOutputTokens = model.maxOutputTokens ?: maxOutputTokens,
            pricePromptPerToken = model.pricePromptPerToken ?: pricePromptPerToken,
            priceCompletionPerToken = model.priceCompletionPerToken ?: priceCompletionPerToken,
            supportsTemperature = model.supportsTemperature ?: reportedTemperature ?: temperature,
            supportsStructuredOutput = model.supportsStructuredOutput ?: reportedStructuredOutput ?: structuredOutput,
            reasoningEfforts = if (model.reasoningEfforts.isNotEmpty()) model.reasoningEfforts else reasoningEfforts,
            reasoningControlTypes = if (model.reasoningControlTypes.isNotEmpty()) model.reasoningControlTypes else reasoningControlTypes,
            advertisedInputModalities = model.advertisedInputModalities.ifEmpty { advertisedInputModalities },
            advertisedOutputModalities = model.advertisedOutputModalities.ifEmpty { advertisedOutputModalities },
            metadataSource = model.metadataSource ?: "models.dev:$providerId/$modelId (partial)",
        )
    }

    companion object {
        internal fun fromProviderModel(providerId: String, id: String, node: JsonObject): ModelsDevModelMetadata {
            fun str(name: String) = node[name]?.jsonPrimitive?.contentOrNull
            fun flag(name: String) = node[name]?.jsonPrimitive?.booleanOrNull
            fun positiveInt(obj: JsonObject?, name: String) =
                obj?.get(name)?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
            fun price(obj: JsonObject?, name: String) =
                obj?.get(name)?.jsonPrimitive?.doubleOrNull
                    ?.takeIf { it.isFinite() && it >= 0 }?.div(1_000_000.0)
            val modalities = node["modalities"] as? JsonObject
            fun advertisedMods(key: String): List<String> =
                (modalities?.get(key) as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.filter { it in setOf("text", "image", "audio", "video", "pdf") }
                    ?.distinct() ?: emptyList()
            fun mods(key: String): List<Modality>? =
                (modalities?.get(key) as? JsonArray)?.let { array ->
                    array.mapNotNull { entry ->
                        when (entry.jsonPrimitive.contentOrNull) {
                            "text" -> Modality.TEXT
                            "image" -> Modality.IMAGE
                            else -> null // Other modalities aren't expressible by this Model schema yet
                        }
                    }.distinct()
                }
            val limits = node["limit"] as? JsonObject
            val costs = node["cost"] as? JsonObject
            val reasoningOptions = (node["reasoning_options"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?: emptyList()
            val reasoningControlTypes = reasoningOptions
                .mapNotNull { it["type"]?.jsonPrimitive?.contentOrNull }
                .distinct()
            val efforts = reasoningOptions
                .firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "effort" }
                ?.get("values")
                ?.let { it as? JsonArray }
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: emptyList()
            val modelType = str("type")
            return ModelsDevModelMetadata(
                providerId = providerId,
                modelId = id,
                displayName = str("name")?.takeIf { it.isNotBlank() } ?: id,
                inputModalities = mods("input"),
                outputModalities = mods("output"),
                toolCalling = flag("tool_call"),
                reasoning = flag("reasoning"),
                structuredOutput = flag("structured_output"),
                temperature = flag("temperature"),
                contextLength = positiveInt(limits, "context"),
                maxOutputTokens = positiveInt(limits, "output"),
                pricePromptPerToken = price(costs, "input"),
                priceCompletionPerToken = price(costs, "output"),
                reasoningEfforts = efforts,
                reasoningControlTypes = reasoningControlTypes,
                advertisedInputModalities = advertisedMods("input"),
                advertisedOutputModalities = advertisedMods("output"),
                isSelectableAsChatOrImage = modelType == null &&
                    ((modalities?.get("output") as? JsonArray)?.any {
                        it.jsonPrimitive.contentOrNull in setOf("text", "image")
                    } == true),
                isDeprecated = str("status") == "deprecated",
            )
        }
    }
}
