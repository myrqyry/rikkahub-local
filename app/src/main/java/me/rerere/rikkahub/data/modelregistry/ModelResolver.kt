package me.rerere.rikkahub.data.modelregistry

data class ModelResolutionRequest(
    val capability: ModelCapability,
    val assistantOverride: String? = null,
    val conversationOverride: String? = null,
    val globalAssignment: String? = null,
    val models: List<ModelDescriptor>,
    val sourcePolicy: ModelSourcePolicy = ModelSourcePolicy.LOCAL_ONLY,
)

sealed interface ModelResolution {
    data class Resolved(val model: ModelDescriptor, val source: ResolutionSource) : ModelResolution
    data class InvalidOverride(val modelId: String, val reason: ModelFailureReason) : ModelResolution
    data class BlockedByPolicy(val modelId: String, val policy: ModelSourcePolicy) : ModelResolution
    data object NoCompatibleModel : ModelResolution
}

enum class ModelSourcePolicy {
    ANY,
    LOCAL_ONLY,
}

enum class ModelFailureReason {
    NOT_FOUND,
    DISABLED,
    INCOMPATIBLE,
    NOT_READY,
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

        fun policyAllows(model: ModelDescriptor): Boolean =
            request.sourcePolicy == ModelSourcePolicy.ANY || model.source is ModelSource.Local

        fun failureReason(model: ModelDescriptor): ModelFailureReason = when {
            !model.providerEnabled -> ModelFailureReason.DISABLED
            !model.supports(capability) -> ModelFailureReason.INCOMPATIBLE
            model.source is ModelSource.Local && model.lifecycle != ModelLifecycle.READY && !model.installed ->
                ModelFailureReason.NOT_READY
            else -> ModelFailureReason.INCOMPATIBLE
        }

        request.assistantOverride?.let { id ->
            val model = byId[id] ?: return ModelResolution.InvalidOverride(id, ModelFailureReason.NOT_FOUND)
            if (!policyAllows(model)) return ModelResolution.BlockedByPolicy(id, request.sourcePolicy)
            if (!model.providerEnabled || !model.supports(capability) ||
                (model.source is ModelSource.Local && model.lifecycle != ModelLifecycle.READY && !model.installed)
            ) {
                return ModelResolution.InvalidOverride(id, failureReason(model))
            }
            return ModelResolution.Resolved(model, ResolutionSource.ASSISTANT_OVERRIDE)
        }

        listOf(
            request.conversationOverride to ResolutionSource.CONVERSATION_OVERRIDE,
            request.globalAssignment to ResolutionSource.GLOBAL_ASSIGNMENT,
        ).forEach { (id, source) ->
            val model = id?.let(byId::get)
            if (model != null && policyAllows(model) && model.canAutoResolve(capability) &&
                model.providerEnabled &&
                (model.source !is ModelSource.Local || model.lifecycle == ModelLifecycle.READY || model.installed)
            ) {
                return ModelResolution.Resolved(model, source)
            }
        }

        val candidates = request.models.asSequence()
            .filter { it.providerEnabled && it.canAutoResolve(capability) }
            .filter { policyAllows(it) }
            .filter {
                it.source !is ModelSource.Local || it.lifecycle == ModelLifecycle.READY || it.installed
            }
            .sortedBy { if (it.source is ModelSource.Local) 0 else 1 }
            .toList()
        return candidates.firstOrNull()?.let {
            ModelResolution.Resolved(it, ResolutionSource.FIRST_ENABLED_COMPATIBLE)
        } ?: ModelResolution.NoCompatibleModel
    }
}
