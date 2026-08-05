package me.rerere.rikkahub.data.modelregistry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.rerere.rikkahub.data.datastore.SettingsStore
import kotlin.uuid.Uuid

interface LegacyModelAssignmentAdapter {
    val titleModelId: StateFlow<String?>
    val translationModelId: StateFlow<String?>

    suspend fun setTitleModel(modelId: String?)
    suspend fun setTranslationModel(modelId: String?)
}

class SettingsLegacyModelAssignmentAdapter(
    private val settingsStore: SettingsStore,
    scope: CoroutineScope,
) : LegacyModelAssignmentAdapter {
    override val titleModelId = settingsStore.settingsFlow
        .map { it.titleModelId?.toString() }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = settingsStore.settingsFlow.value.titleModelId?.toString(),
        )

    override val translationModelId = settingsStore.settingsFlow
        .map { it.translateModeId.toString() }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = settingsStore.settingsFlow.value.translateModeId.toString(),
        )

    override suspend fun setTitleModel(modelId: String?) {
        settingsStore.update { settings ->
            settings.copy(titleModelId = modelId?.let(Uuid::parse))
        }
    }

    override suspend fun setTranslationModel(modelId: String?) {
        if (modelId == null) {
            throw UnsupportedOperationException("Translation model assignment cannot be cleared")
        }
        val modelUuid = Uuid.parse(modelId)
        settingsStore.update { settings -> settings.copy(translateModeId = modelUuid) }
    }
}
