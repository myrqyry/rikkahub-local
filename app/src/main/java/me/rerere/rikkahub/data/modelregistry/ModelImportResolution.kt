package me.rerere.rikkahub.data.modelregistry

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelRegistry
import kotlin.uuid.Uuid

/**
 * The provider's /models list and models.dev metadata load independently.
 * Never persist a model with fallback capabilities just because metadata is
 * still loading. A completed catalog attempt (including offline/unavailable)
 * permits import, using exact-provider metadata when found.
 */
sealed interface ModelCatalogLoad {
    data object Loading : ModelCatalogLoad
    data class Ready(val catalog: ModelsDevModelCatalog?) : ModelCatalogLoad
}

/** Compose produceState can retain its previous value for one composition after a key change. */
data class ProviderScopedCatalogLoad(
    val providerId: Uuid,
    val endpoint: String?,
    val load: ModelCatalogLoad,
) {
    fun forProvider(id: Uuid, currentEndpoint: String?): ModelCatalogLoad =
        if (providerId == id && endpoint == currentEndpoint) load else ModelCatalogLoad.Loading
}

/** Never display a previous provider/API-key's /models response during recomposition. */
data class ProviderScopedModelList(
    val source: ProviderSetting,
    val models: List<Model>,
) {
    fun forProvider(current: ProviderSetting): List<Model> =
        if (source == current) models else emptyList()
}

fun ModelCatalogLoad.resolveImport(model: Model, provider: ProviderSetting): Model? {
    val ready = this as? ModelCatalogLoad.Ready ?: return null
    val metadata = ready.catalog?.find(provider, model.modelId)
        ?.takeIf { it.isSelectableAsChatOrImage }
    if (metadata != null) return metadata.enrichProviderModel(model)

    // The provider's rich /models payload outranks any static name heuristics.
    return if (model.supportedParameters.isNotEmpty() ||
        model.metadataSource?.startsWith("provider:") == true) model else model.copy(
        inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId),
        outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId),
        abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId),
    )
}
