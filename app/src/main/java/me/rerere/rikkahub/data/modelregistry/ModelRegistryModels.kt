package me.rerere.rikkahub.data.modelregistry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.locallm.LocalRuntime

@Serializable
enum class ModelCapability {
    CHAT,
    REASONING,
    TOOLS,
    VISION,
    OCR,
    DOCUMENT_ANALYSIS,
    IMAGE_GENERATION,
    IMAGE_EDITING,
    TEXT_TO_SPEECH,
    SPEECH_TO_TEXT,
    AUDIO_UNDERSTANDING,
    EMBEDDINGS,
    RERANKING,
}

enum class ModelLifecycle {
    AVAILABLE,
    DOWNLOADING,
    INSTALLED,
    VERIFYING,
    READY,
    INCOMPATIBLE,
    ERROR,
}

sealed interface ModelSource {
    data class Local(
        val runtime: LocalRuntime,
        val files: List<String> = emptyList(),
    ) : ModelSource

    data class Cloud(
        val providerId: String,
        val remoteModelId: String,
    ) : ModelSource
}

@Serializable
@JvmInline
value class RegistryModelId(val value: String)

@Serializable
enum class ModelRole {
    @SerialName("chat")
    CHAT,
    @SerialName("vision")
    VISION,
    @SerialName("ocr")
    OCR,
    @SerialName("image_generation")
    IMAGE_GENERATION,
    @SerialName("image_editing")
    IMAGE_EDITING,
    @SerialName("text_to_speech")
    TEXT_TO_SPEECH,
    @SerialName("speech_to_text")
    SPEECH_TO_TEXT,
    @SerialName("embeddings")
    EMBEDDINGS,
}

data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val source: ModelSource,
    val capabilities: Set<ModelCapability>,
    val enabledCapabilities: Set<ModelCapability> = capabilities,
    val lifecycle: ModelLifecycle = ModelLifecycle.AVAILABLE,
    val providerEnabled: Boolean = true,
    val installed: Boolean = false,
    val loaded: Boolean = false,
    val connected: Boolean = false,
    val selected: Boolean = false,
    val unverifiedCapabilities: Set<ModelCapability> = emptySet(),
    val metadata: Map<String, String> = emptyMap(),
) {
    fun supports(capability: ModelCapability): Boolean =
        capability in capabilities && capability in enabledCapabilities

    fun canAutoResolve(capability: ModelCapability): Boolean =
        supports(capability) && capability !in unverifiedCapabilities

    fun canExplicitlySelect(capability: ModelCapability): Boolean =
        supports(capability)
}

data class ModelAssignments(
    val defaults: Map<ModelRole, String?> = emptyMap(),
    /** Existing settings assignments that have no corresponding ModelRole yet. */
    val legacyDefaults: Map<String, String?> = emptyMap(),
)

data class ModelProviderDescriptor(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
    val modelIds: List<String>,
)

fun ModelRole.capability(): ModelCapability = when (this) {
    ModelRole.CHAT -> ModelCapability.CHAT
    ModelRole.VISION -> ModelCapability.VISION
    ModelRole.OCR -> ModelCapability.OCR
    ModelRole.IMAGE_GENERATION -> ModelCapability.IMAGE_GENERATION
    ModelRole.IMAGE_EDITING -> ModelCapability.IMAGE_EDITING
    ModelRole.TEXT_TO_SPEECH -> ModelCapability.TEXT_TO_SPEECH
    ModelRole.SPEECH_TO_TEXT -> ModelCapability.SPEECH_TO_TEXT
    ModelRole.EMBEDDINGS -> ModelCapability.EMBEDDINGS
}
