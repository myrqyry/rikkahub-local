package me.rerere.rikkahub.data.modelregistry

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant

class ModelRoleResolver(
    private val registry: ModelRegistry,
) {
    fun resolve(
        role: ModelRole,
        assistant: Assistant,
        @Suppress("UNUSED_PARAMETER") settings: Settings,
        sourcePolicy: ModelSourcePolicy = ModelSourcePolicy.ANY,
    ): ModelResolution {
        if (role == ModelRole.CHAT || role == ModelRole.TEXT_TO_SPEECH || role == ModelRole.SPEECH_TO_TEXT) {
            return ModelResolution.NoCompatibleModel
        }
        val assignments = registry.assignments.value.defaults
        return ModelResolver.resolve(
            ModelResolutionRequest(
                capability = role.capability(),
                assistantOverride = assistant.modelOverrides[role]?.value,
                globalAssignment = assignments[role],
                models = registry.models.value,
                sourcePolicy = sourcePolicy,
            ),
        )
    }
}
