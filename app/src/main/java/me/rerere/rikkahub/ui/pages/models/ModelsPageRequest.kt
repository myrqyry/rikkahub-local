package me.rerere.rikkahub.ui.pages.models

import me.rerere.rikkahub.data.modelregistry.ModelCapability

enum class ModelTab { ALL, CHAT, VISION, IMAGE, SPEECH, EMBEDDINGS }

enum class ModelSourceFilter { ALL, LOCAL, CLOUD }

enum class ModelsFocus { ASSIGNMENTS, MODELS }

data class ModelsPageRequest(
    val tab: ModelTab = ModelTab.ALL,
    val search: String = "",
    val source: ModelSourceFilter = ModelSourceFilter.ALL,
    val providerId: String? = null,
    val focus: ModelsFocus? = null,
    val modelId: String? = null,
)

fun ModelTab.capability(): ModelCapability? = when (this) {
    ModelTab.ALL -> null
    ModelTab.CHAT -> ModelCapability.CHAT
    ModelTab.VISION -> ModelCapability.VISION
    ModelTab.IMAGE -> ModelCapability.IMAGE_GENERATION
    ModelTab.SPEECH -> null
    ModelTab.EMBEDDINGS -> ModelCapability.EMBEDDINGS
}
