package me.rerere.rikkahub.data.modelregistry

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.datastore.SettingsStore
import kotlin.uuid.Uuid

interface LegacyModelAssignmentAdapter {
    val titleModelId: Flow<String?>
    val translationModelId: Flow<String?>

    suspend fun setTitleModel(modelId: String?)
    suspend fun setTranslationModel(modelId: String?)
}

class SettingsLegacyModelAssignmentAdapter(
    private val settingsStore: SettingsStore,
) : LegacyModelAssignmentAdapter {
    override val titleModelId = settingsStore.settingsFlow
        .map { it.titleModelId?.toString() }
        .distinctUntilChanged()

    override val translationModelId = settingsStore.settingsFlow
        .map { it.translateModeId.toString() }
        .distinctUntilChanged()

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
