package me.rerere.rikkahub.ui.pages.models

import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor

enum class ModelsFilter {
    ALL, CHAT, VISION, IMAGE, AUDIO, EMBEDDINGS;

    fun matches(model: ModelDescriptor): Boolean = when (this) {
        ALL -> true
        CHAT -> model.supports(ModelCapability.CHAT)
        VISION -> model.capabilities.any {
            it == ModelCapability.VISION ||
                it == ModelCapability.OCR ||
                it == ModelCapability.DOCUMENT_ANALYSIS
        }
        IMAGE -> model.capabilities.any {
            it == ModelCapability.IMAGE_GENERATION || it == ModelCapability.IMAGE_EDITING
        }
        AUDIO -> model.capabilities.any {
            it == ModelCapability.TEXT_TO_SPEECH ||
                it == ModelCapability.SPEECH_TO_TEXT ||
                it == ModelCapability.AUDIO_UNDERSTANDING
        }
        EMBEDDINGS -> model.capabilities.contains(ModelCapability.EMBEDDINGS)
    }
}

fun ModelTab.toModelsFilter(): ModelsFilter = when (this) {
    ModelTab.ALL -> ModelsFilter.ALL
    ModelTab.CHAT -> ModelsFilter.CHAT
    ModelTab.VISION -> ModelsFilter.VISION
    ModelTab.IMAGE -> ModelsFilter.IMAGE
    ModelTab.SPEECH -> ModelsFilter.AUDIO
    ModelTab.EMBEDDINGS -> ModelsFilter.EMBEDDINGS
    ModelTab.TASK -> ModelsFilter.VISION
    ModelTab.OTHER -> ModelsFilter.ALL
}

fun searchMatches(model: ModelDescriptor, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return model.displayName.lowercase().contains(q) || model.id.lowercase().contains(q)
}
