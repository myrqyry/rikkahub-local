package me.rerere.rikkahub.ui.pages.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.modelregistry.LegacyModelAssignmentAdapter
import me.rerere.rikkahub.data.modelregistry.ModelAssignments
import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelProviderDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelRegistry
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.data.modelregistry.capability

data class LegacyAssignments(
    val titleModelId: String? = null,
    val translationModelId: String? = null,
)

sealed interface RepairState {
    data class ModelUnavailable(val role: ModelRole, val modelId: String) : RepairState
    data class LegacyModelUnavailable(val key: LegacyAssignmentKey, val modelId: String) : RepairState
}

enum class LegacyAssignmentKey { TITLE, TRANSLATION }

class UnifiedModelsViewModel(
    private val registry: ModelRegistry,
    private val legacyAdapter: LegacyModelAssignmentAdapter,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val ownerScope = scope ?: viewModelScope
    private val tab = MutableStateFlow(ModelTab.ALL)
    private val search = MutableStateFlow("")
    private val source = MutableStateFlow(ModelSourceFilter.ALL)
    private val providerId = MutableStateFlow<String?>(null)

    val selectedTab: StateFlow<ModelTab> = tab.asStateFlow()
    val searchText: StateFlow<String> = search.asStateFlow()
    val sourceFilter: StateFlow<ModelSourceFilter> = source.asStateFlow()
    val providerFilter: StateFlow<String?> = providerId.asStateFlow()
    val allModels: StateFlow<List<ModelDescriptor>> = registry.models
    val registryProviders: StateFlow<List<ModelProviderDescriptor>> = registry.providers

    val visibleModels: StateFlow<List<ModelDescriptor>> = combine(
        registry.models,
        registry.providers,
        combine(tab, search, source, providerId) { selectedTab, query, sourceFilter, selectedProvider ->
            FilterState(selectedTab, query, sourceFilter, selectedProvider)
        },
    ) { models, providers, filter ->
        val selectedTab = filter.tab
        val query = filter.query
        val sourceFilter = filter.source
        val selectedProvider = filter.providerId
        val enabledProviders = providers.filter { it.enabled }.map { it.id }.toSet()
        val capability = selectedTab.capability()
        val normalizedQuery = query.trim().lowercase()
        models.filter { model ->
            val modelProvider = (model.source as? ModelSource.Cloud)?.providerId
            model.providerEnabled && (modelProvider == null || modelProvider in enabledProviders)
        }.filter { model ->
            selectedProvider == null || (model.source as? ModelSource.Cloud)?.providerId == selectedProvider
        }.filter { model ->
            when (sourceFilter) {
                ModelSourceFilter.ALL -> true
                ModelSourceFilter.LOCAL -> model.source is ModelSource.Local
                ModelSourceFilter.CLOUD -> model.source is ModelSource.Cloud
            }
        }.filter { model ->
            capability?.let(model::supports) ?: when (selectedTab) {
                ModelTab.SPEECH -> model.supports(ModelCapability.TEXT_TO_SPEECH) ||
                    model.supports(ModelCapability.SPEECH_TO_TEXT) ||
                    model.supports(ModelCapability.AUDIO_UNDERSTANDING)
                ModelTab.OTHER -> model.enabledCapabilities.none { it in setOf(
                    ModelCapability.CHAT,
                    ModelCapability.VISION,
                    ModelCapability.IMAGE_GENERATION,
                    ModelCapability.TEXT_TO_SPEECH,
                    ModelCapability.SPEECH_TO_TEXT,
                    ModelCapability.AUDIO_UNDERSTANDING,
                    ModelCapability.EMBEDDINGS,
                ) }
                else -> true
            }
        }.filter { model ->
            normalizedQuery.isEmpty() || model.displayName.lowercase().contains(normalizedQuery) ||
                model.id.lowercase().contains(normalizedQuery)
        }
    }.stateIn(ownerScope, SharingStarted.Eagerly, emptyList())

    // Model Manager view: every registered model discoverable, grouped by source/provider.
    // Unlike visibleModels (assignment candidates) this does NOT drop disabled models or
    // models under disabled providers, so users can re-enable them from the inventory.
    // Tab matching uses full capabilities (what the model CAN do), not enabledCapabilities.
    val managerVisibleModels: StateFlow<List<ModelDescriptor>> = combine(
        registry.models,
        combine(tab, search, providerId) { selectedTab, query, selectedProvider ->
            ManagerFilter(selectedTab, query, selectedProvider)
        },
    ) { models, filter ->
        val normalizedQuery = filter.query.trim().lowercase()
        models.filter { model -> managerMatchesTab(model, filter.tab) }
            .filter { model ->
                filter.providerId == null ||
                    (model.source as? ModelSource.Cloud)?.providerId == filter.providerId
            }
            .filter { model ->
                normalizedQuery.isEmpty() || model.displayName.lowercase().contains(normalizedQuery) ||
                    model.id.lowercase().contains(normalizedQuery)
            }
    }.stateIn(ownerScope, SharingStarted.Eagerly, emptyList())

    val assignments: StateFlow<ModelAssignments> = registry.assignments
    val legacyAssignments: StateFlow<LegacyAssignments> = combine(
        legacyAdapter.titleModelId, legacyAdapter.translationModelId,
    ) { title, translation -> LegacyAssignments(title, translation) }
        .stateIn(ownerScope, SharingStarted.Eagerly, LegacyAssignments())

    val repairState: StateFlow<RepairState?> = combine(
        registry.models,
        registry.assignments,
        legacyAdapter.titleModelId,
        legacyAdapter.translationModelId,
    ) { models, current, titleId, translationId -> repairStateFor(models, current, titleId, translationId)
    }.stateIn(
        ownerScope,
        SharingStarted.Eagerly,
        repairStateFor(
            registry.models.value,
            registry.assignments.value,
            legacyAdapter.titleModelId.value,
            legacyAdapter.translationModelId.value,
        ),
    )

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    fun setTab(value: ModelTab) { tab.value = value }
    fun setSearch(value: String) { search.value = value }
    fun clearSearch() { search.value = "" }
    fun setSourceFilter(value: ModelSourceFilter) { source.value = value }
    fun clearSourceFilter() { source.value = ModelSourceFilter.ALL }
    fun setProviderFilter(value: String?) { providerId.value = value }
    fun clearProviderFilter() { providerId.value = null }
    fun clearOperationError() { _operationError.value = null }

    fun setModelEnabled(model: ModelDescriptor, enabled: Boolean) = ownerScope.launch {
        _operationError.value = null
        try {
            model.capabilities.forEach { capability ->
                registry.setCapabilityEnabled(model.id, capability, enabled)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _operationError.value = error.message ?: error::class.simpleName
        }
    }

    fun setCapabilityEnabled(modelId: String, capability: ModelCapability, enabled: Boolean) = ownerScope.launch {
        _operationError.value = null
        try {
            registry.setCapabilityEnabled(modelId, capability, enabled)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _operationError.value = error.message ?: error::class.simpleName
        }
    }

    fun refreshProvider(providerId: String) = ownerScope.launch {
        _operationError.value = null
        try {
            registry.refreshProvider(providerId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _operationError.value = error.message ?: error::class.simpleName
        }
    }

    fun renameLocalModel(modelId: String, newName: String) = ownerScope.launch {
        _operationError.value = null
        try {
            registry.rename(modelId, newName)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _operationError.value = error.message ?: error::class.simpleName
        }
    }

    fun deleteLocalModel(modelId: String) = ownerScope.launch {
        _operationError.value = null
        try {
            registry.remove(modelId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _operationError.value = error.message ?: error::class.simpleName
        }
    }

    fun assign(role: ModelRole, modelId: String?) = ownerScope.launch {
        _operationError.value = null
        try {
            validate(modelId, role.capability())
            registry.assign(role, modelId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _operationError.value = error.message ?: error::class.simpleName
        }
    }

    fun assignTitle(modelId: String?) = assignLegacy(LegacyAssignmentKey.TITLE, modelId)
    fun assignTranslation(modelId: String?) = assignLegacy(LegacyAssignmentKey.TRANSLATION, modelId)

    fun assignLegacy(key: LegacyAssignmentKey, modelId: String?) = ownerScope.launch {
        _operationError.value = null
        try {
            validate(modelId, ModelCapability.CHAT)
            when (key) {
                LegacyAssignmentKey.TITLE -> legacyAdapter.setTitleModel(modelId)
                LegacyAssignmentKey.TRANSLATION -> legacyAdapter.setTranslationModel(modelId)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _operationError.value = error.message ?: error::class.simpleName
        }
    }

    private fun validate(modelId: String?, capability: ModelCapability) {
        if (modelId == null) return
        val model = registry.models.value.firstOrNull { it.id == modelId }
            ?: error("Unknown model: $modelId")
        check(model.providerEnabled) { "Model $modelId provider is disabled" }
        check(model.supports(capability)) { "Model $modelId does not support $capability" }
        check(model.source !is ModelSource.Local || model.lifecycle == ModelLifecycle.READY) {
            "Model $modelId is not ready"
        }
    }

    private fun legacyRepair(
        models: List<ModelDescriptor>,
        key: LegacyAssignmentKey,
        modelId: String?,
    ): RepairState? {
        if (modelId == null) return null
        val model = models.firstOrNull { it.id == modelId }
        return if (model == null || !modelUsable(model, ModelCapability.CHAT)) {
            RepairState.LegacyModelUnavailable(key, modelId)
        } else null
    }

    private fun repairStateFor(
        models: List<ModelDescriptor>,
        current: ModelAssignments,
        titleId: String?,
        translationId: String?,
    ): RepairState? = current.defaults.entries.firstNotNullOfOrNull { (role, modelId) ->
        modelId?.let { id ->
            val model = models.firstOrNull { it.id == id }
            if (model == null || !usableFor(model, role)) RepairState.ModelUnavailable(role, id) else null
        }
    } ?: legacyRepair(models, LegacyAssignmentKey.TITLE, titleId)
        ?: legacyRepair(models, LegacyAssignmentKey.TRANSLATION, translationId)

    private fun modelUsable(model: ModelDescriptor, capability: ModelCapability): Boolean =
        model.providerEnabled && model.supports(capability) &&
            (model.source !is ModelSource.Local || model.lifecycle == ModelLifecycle.READY)

    private fun usableFor(model: ModelDescriptor, role: ModelRole): Boolean =
        modelUsable(model, role.capability())

    private fun managerMatchesTab(model: ModelDescriptor, tab: ModelTab): Boolean =
        when (tab) {
            ModelTab.ALL -> true
            ModelTab.CHAT -> model.capabilities.contains(ModelCapability.CHAT)
            ModelTab.IMAGE -> model.capabilities.contains(ModelCapability.IMAGE_GENERATION) ||
                model.capabilities.contains(ModelCapability.IMAGE_EDITING)
            ModelTab.EMBEDDINGS -> model.capabilities.contains(ModelCapability.EMBEDDINGS)
            ModelTab.TASK -> model.capabilities.any {
                it == ModelCapability.OCR ||
                    it == ModelCapability.DOCUMENT_ANALYSIS ||
                    it == ModelCapability.RERANKING
            }
            ModelTab.VISION -> model.capabilities.contains(ModelCapability.VISION)
            ModelTab.SPEECH -> model.capabilities.any {
                it == ModelCapability.TEXT_TO_SPEECH ||
                    it == ModelCapability.SPEECH_TO_TEXT ||
                    it == ModelCapability.AUDIO_UNDERSTANDING
            }
            ModelTab.OTHER -> model.capabilities.none {
                it == ModelCapability.CHAT ||
                    it == ModelCapability.VISION ||
                    it == ModelCapability.IMAGE_GENERATION ||
                    it == ModelCapability.TEXT_TO_SPEECH ||
                    it == ModelCapability.SPEECH_TO_TEXT ||
                    it == ModelCapability.AUDIO_UNDERSTANDING
            }
        }

    private data class FilterState(
        val tab: ModelTab,
        val query: String,
        val source: ModelSourceFilter,
        val providerId: String?,
    )

    private data class ManagerFilter(
        val tab: ModelTab,
        val query: String,
        val providerId: String?,
    )
}
