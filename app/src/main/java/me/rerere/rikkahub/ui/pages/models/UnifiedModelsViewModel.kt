package me.rerere.rikkahub.ui.pages.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
}

class UnifiedModelsViewModel(
    private val registry: ModelRegistry,
    private val legacyAdapter: LegacyModelAssignmentAdapter,
    request: ModelsPageRequest = ModelsPageRequest(),
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val ownerScope = scope ?: viewModelScope
    private val tab = MutableStateFlow(request.tab)
    private val search = MutableStateFlow(request.search)
    private val source = MutableStateFlow(request.source)
    private val providerId = MutableStateFlow(request.providerId)

    val selectedTab: StateFlow<ModelTab> = tab.asStateFlow()
    val searchText: StateFlow<String> = search.asStateFlow()
    val sourceFilter: StateFlow<ModelSourceFilter> = source.asStateFlow()
    val providerFilter: StateFlow<String?> = providerId.asStateFlow()
    val allModels: StateFlow<List<ModelDescriptor>> = registry.models

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
            modelProvider == null || modelProvider in enabledProviders
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
                else -> true
            }
        }.filter { model ->
            normalizedQuery.isEmpty() || model.displayName.lowercase().contains(normalizedQuery) ||
                model.id.lowercase().contains(normalizedQuery)
        }
    }.stateIn(ownerScope, SharingStarted.Eagerly, emptyList())

    val assignments: StateFlow<ModelAssignments> = registry.assignments
    val legacyAssignments: StateFlow<LegacyAssignments> = combine(
        legacyAdapter.titleModelId, legacyAdapter.translationModelId,
    ) { title, translation -> LegacyAssignments(title, translation) }
        .stateIn(ownerScope, SharingStarted.Eagerly, LegacyAssignments())

    val repairState: StateFlow<RepairState?> = combine(registry.models, registry.assignments) { models, current ->
        current.defaults.entries.firstNotNullOfOrNull { (role, modelId) ->
            modelId?.let { id ->
                val model = models.firstOrNull { it.id == id }
                if (model == null || !usableFor(model, role)) RepairState.ModelUnavailable(role, id) else null
            }
        }
    }.stateIn(ownerScope, SharingStarted.Eagerly, null)

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

    fun assign(role: ModelRole, modelId: String?) = ownerScope.launch {
        _operationError.value = null
        try {
            if (modelId != null) {
                val model = registry.models.value.firstOrNull { it.id == modelId }
                    ?: error("Unknown model: $modelId")
                check(usableFor(model, role)) { "Model $modelId is not compatible with $role" }
            }
            registry.assign(role, modelId)
        } catch (error: Throwable) {
            _operationError.value = error.message ?: error::class.simpleName
        }
    }

    fun assignTitle(modelId: String?) = ownerScope.launch {
        _operationError.value = null
        try { legacyAdapter.setTitleModel(modelId) } catch (error: Throwable) {
            _operationError.value = error.message ?: error::class.simpleName
        }
    }

    fun assignTranslation(modelId: String?) = ownerScope.launch {
        _operationError.value = null
        try { legacyAdapter.setTranslationModel(modelId) } catch (error: Throwable) {
            _operationError.value = error.message ?: error::class.simpleName
        }
    }

    private fun usableFor(model: ModelDescriptor, role: ModelRole): Boolean =
        model.providerEnabled && model.supports(role.capability()) &&
            (model.source !is ModelSource.Local || model.lifecycle == ModelLifecycle.READY)

    private data class FilterState(
        val tab: ModelTab,
        val query: String,
        val source: ModelSourceFilter,
        val providerId: String?,
    )
}
