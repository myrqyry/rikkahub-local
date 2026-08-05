package me.rerere.rikkahub.data.modelregistry

data class ModelResolutionRequest(
    val capability: ModelCapability,
    val assistantOverride: String? = null,
    val conversationOverride: String? = null,
    val globalAssignment: String? = null,
    val models: List<ModelDescriptor>,
    val allowCloudFallback: Boolean = false,
)

sealed interface ModelResolution {
    data class Resolved(val model: ModelDescriptor, val source: ResolutionSource) : ModelResolution
    data object NoCompatibleModel : ModelResolution
}

enum class ResolutionSource {
    ASSISTANT_OVERRIDE,
    CONVERSATION_OVERRIDE,
    GLOBAL_ASSIGNMENT,
    FIRST_ENABLED_COMPATIBLE,
}

object ModelResolver {
    fun resolve(request: ModelResolutionRequest): ModelResolution {
        val byId = request.models.associateBy { it.id }
        val capability = request.capability

        fun compatible(id: String?): ModelDescriptor? = id?.let(byId::get)?.takeIf {
            it.providerEnabled && it.supports(capability) &&
                (it.source !is ModelSource.Local || it.lifecycle == ModelLifecycle.READY || it.installed)
        }

        listOf(
            request.assistantOverride to ResolutionSource.ASSISTANT_OVERRIDE,
            request.conversationOverride to ResolutionSource.CONVERSATION_OVERRIDE,
            request.globalAssignment to ResolutionSource.GLOBAL_ASSIGNMENT,
        ).forEach { (id, source) ->
            compatible(id)?.let { return ModelResolution.Resolved(it, source) }
        }

        val candidates = request.models.asSequence()
            .filter { it.providerEnabled && it.supports(capability) }
            .filter { request.allowCloudFallback || it.source is ModelSource.Local }
            .filter {
                it.source !is ModelSource.Local || it.lifecycle == ModelLifecycle.READY || it.installed
            }
            .toList()
        return candidates.firstOrNull()?.let {
            ModelResolution.Resolved(it, ResolutionSource.FIRST_ENABLED_COMPATIBLE)
        } ?: ModelResolution.NoCompatibleModel
    }
}
