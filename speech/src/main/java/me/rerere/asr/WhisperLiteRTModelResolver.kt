package me.rerere.asr

import java.io.File

data class WhisperLiteRTModelStatus(
    val exists: Boolean,
    val empty: Boolean,
    val selected: WhisperLiteRTModelEntry?,
    val actual: WhisperLiteRTModelEntry?,
    val warning: String?,
)

fun resolveWhisperLiteRTModel(
    setting: ASRProviderSetting.WhisperLiteRT,
): WhisperLiteRTModelStatus {
    val file = File(setting.modelPath)
    val exists = file.isFile
    val selected = WhisperLiteRTModelCatalog.findById(setting.modelId)
    val actual = if (exists) WhisperLiteRTModelCatalog.findByFilename(file.name) else null
    val warning = if (exists && selected != null && actual != null && selected.id != actual.id) {
        "Selected ${selected.displayName}, but the file is ${actual.displayName}"
    } else if (exists && selected != null && actual == null && setting.modelId != WhisperLiteRTModelCatalog.CUSTOM_ID) {
        "Selected ${selected.displayName}, but the file name is not recognized"
    } else {
        null
    }
    return WhisperLiteRTModelStatus(
        exists = exists,
        empty = exists && file.length() == 0L,
        selected = selected,
        actual = actual,
        warning = warning,
    )
}

fun describeWhisperLiteRTModelError(
    setting: ASRProviderSetting.WhisperLiteRT,
    status: WhisperLiteRTModelStatus = resolveWhisperLiteRTModel(setting),
): String = when {
    setting.modelPath.isBlank() -> "Whisper LiteRT model path is empty"
    !status.exists -> "Whisper LiteRT model not found: ${setting.modelPath}"
    status.empty -> "Whisper LiteRT model file is empty: ${setting.modelPath}"
    status.warning != null -> status.warning
    else -> "Whisper LiteRT model is ready"
}
