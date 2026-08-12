package me.rerere.rikkahub.data.modelregistry

import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Modality

data class InferredCapabilities(
    val verified: Set<ModelCapability>,
    val unverified: Set<ModelCapability>,
)

object ModelCapabilityInference {
    fun infer(
        model: Model,
        providerCapabilities: Set<ModelCapability> = emptySet(),
        providerUnverifiedCapabilities: Set<ModelCapability> = emptySet(),
    ): InferredCapabilities {
        val verified = buildSet {
            val modelName = model.modelId.lowercase()
            if ("whisper" in modelName) add(ModelCapability.SPEECH_TO_TEXT)
            if (model.type == ModelType.CHAT) add(ModelCapability.CHAT)
            if (model.type == ModelType.EMBEDDING) add(ModelCapability.EMBEDDINGS)
            if (model.abilities.contains(ModelAbility.REASONING)) add(ModelCapability.REASONING)
            if (model.abilities.contains(ModelAbility.TOOL)) add(ModelCapability.TOOLS)
            if (model.tools.contains(BuiltInTools.ImageGeneration)) {
                add(ModelCapability.IMAGE_GENERATION)
            }
            if (model.outputModalities.contains(Modality.IMAGE)) {
                add(ModelCapability.IMAGE_GENERATION)
            }
            addAll(providerCapabilities)
        }

        // Image input is evidence for vision, but not proof of OCR/document support.
        val unverified = buildSet {
            if (model.inputModalities.contains(Modality.IMAGE)) {
                add(ModelCapability.VISION)
                add(ModelCapability.OCR)
                add(ModelCapability.DOCUMENT_ANALYSIS)
            }
            addAll(providerUnverifiedCapabilities)
        } - verified

        return InferredCapabilities(verified, unverified)
    }
}
