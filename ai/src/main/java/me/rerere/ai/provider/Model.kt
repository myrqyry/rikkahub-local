package me.rerere.ai.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Model(
    val modelId: String = "",
    val displayName: String = "",
    val id: Uuid = Uuid.random(),
    val type: ModelType = ModelType.CHAT,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val inputModalities: List<Modality> = listOf(Modality.TEXT),
    val outputModalities: List<Modality> = listOf(Modality.TEXT),
    val abilities: List<ModelAbility> = emptyList(),
    val tools: Set<BuiltInTools> = emptySet(),
    val providerOverwrite: ProviderSetting? = null,
    // Optional capability/pricing metadata, populated from OpenRouter's /models endpoint.
    val contextLength: Int? = null,
    val supportedParameters: List<String> = emptyList(),
    val pricePromptPerToken: Double? = null,
    val priceCompletionPerToken: Double? = null,
    // Optional catalog metadata. Null means unknown, not unsupported. Catalog information
    // is only applied to newly imported models; user-edited persisted models stay unchanged.
    val maxOutputTokens: Int? = null,
    val supportsTemperature: Boolean? = null,
    val supportsStructuredOutput: Boolean? = null,
    val reasoningEfforts: List<String> = emptyList(),
    val metadataSource: String? = null,
    // Runtime metadata: keeps the format/runtime/execution-backend/accelerator
    // distinction on the model itself instead of inferring it from the file
    // extension. format is the model file format (litertlm | gguf), runtime is
    // the inference engine (liteRT | stable-diffusion | llama.cpp),
    // executionBackend is the compute backend (litert | xnnpack | ggml), and
    // hardwareAccelerator is the device target (cpu | gpu | npu | qnn | nnapi).
    val format: String? = null,
    val runtime: String? = null,
    val executionBackend: String? = null,
    val hardwareAccelerator: String? = null,
)

/** Catalog metadata overrides the legacy family heuristic only when explicitly known. */
fun Model.allowsTemperature(legacyAllows: Boolean): Boolean =
    supportsTemperature ?: legacyAllows

/** Unknown limits retain provider behavior; known published limits constrain explicit requests. */
fun Model.clampRequestedMaxTokens(requested: Int?): Int? =
    if (requested != null && maxOutputTokens != null) requested.coerceAtMost(maxOutputTokens)
    else requested

@Serializable
enum class ModelType {
    CHAT,
    IMAGE,
    EMBEDDING,
}

@Serializable
enum class Modality {
    TEXT,
    IMAGE,
}

@Serializable
enum class ModelAbility {
    TOOL,
    REASONING,
}

// 模型(提供商)提供的内置工具选项
@Serializable
sealed class BuiltInTools {
    // https://ai.google.dev/gemini-api/docs/google-search?hl=zh-cn
    @Serializable
    @SerialName("search")
    data object Search : BuiltInTools()

    // https://ai.google.dev/gemini-api/docs/url-context?hl=zh-cn
    @Serializable
    @SerialName("url_context")
    data object UrlContext : BuiltInTools()

    @Serializable
    @SerialName("image_generation")
    data object ImageGeneration : BuiltInTools()
}



