package me.rerere.rikkahub.ui.pages.models

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.modelregistry.ModelCapability

@Serializable
enum class ModelTab { ALL, CHAT, VISION, IMAGE, SPEECH, EMBEDDINGS, OTHER }

@Serializable
enum class ModelSourceFilter { ALL, LOCAL, CLOUD }

@Serializable
enum class ModelsFocus { ASSIGNMENTS, MODELS }

@Serializable
data class ModelsPageRequest(
    val tab: ModelTab = ModelTab.ALL,
    val search: String = "",
    val source: ModelSourceFilter = ModelSourceFilter.ALL,
    val providerId: String? = null,
    val focus: ModelsFocus? = null,
    val modelId: String? = null,
)

@Serializable
data class ModelManagerRequest(
    val tab: ModelTab = ModelTab.ALL,
    val providerId: String? = null,
    val search: String = "",
)

fun ModelTab.capability(): ModelCapability? = when (this) {
    ModelTab.ALL -> null
    ModelTab.CHAT -> ModelCapability.CHAT
    ModelTab.VISION -> ModelCapability.VISION
    ModelTab.IMAGE -> ModelCapability.IMAGE_GENERATION
    ModelTab.SPEECH -> null
    ModelTab.EMBEDDINGS -> ModelCapability.EMBEDDINGS
    ModelTab.OTHER -> null
}
