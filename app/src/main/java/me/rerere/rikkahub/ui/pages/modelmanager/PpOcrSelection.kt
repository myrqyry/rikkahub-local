package me.rerere.rikkahub.ui.pages.modelmanager

import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings

/**
 * Pure, repeatable OCR role assignment; does not touch live settings until the
 * caller explicitly passes the result to SettingsStore.update.
 */
internal fun Settings.withPpOcrSelected(detPath: String, recPath: String): Settings {
    require(detPath.isNotBlank() && recPath.isNotBlank())
    val old = providers.filterIsInstance<ProviderSetting.TaskOcrLocal>().firstOrNull()
    val model = old?.models?.firstOrNull {
        it.modelId == PpOcrVerifiedPackage.MODEL_ID
    } ?: Model(
        modelId = PpOcrVerifiedPackage.MODEL_ID,
        displayName = "PP-OCRv5 · on device",
        type = ModelType.IMAGE,
        inputModalities = listOf(Modality.IMAGE),
        outputModalities = listOf(Modality.TEXT),
        format = "tflite",
        runtime = "LiteRT",
    )
    val updated = if (old != null) {
        old.copy(
            enabled = true,
            detModelPath = detPath,
            recModelPath = recPath,
            models = old.models.filterNot { it.modelId == model.modelId } + model,
        )
    } else {
        ProviderSetting.TaskOcrLocal(
            enabled = true,
            detModelPath = detPath,
            recModelPath = recPath,
            models = listOf(model),
        )
    }
    return copy(
        providers = if (old == null) providers + updated
            else providers.map { if (it.id == old.id) updated else it },
        ocrModelId = model.id,
    )
}
