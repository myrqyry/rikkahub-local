package me.rerere.rikkahub.ui.pages.setting

import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.datastore.DEFAULT_SYSTEM_TTS_ID

/** Mirrors Settings.getSelectedTTSProvider()/getSelectedASRProvider() without persisting a choice. */
internal data class SpeechProviderSelection(
    val effectiveId: Uuid?,
    val isFallback: Boolean,
)

internal fun resolveSpeechProviderSelection(
    configuredId: Uuid?,
    providerIds: List<Uuid>,
): SpeechProviderSelection {
    val effectiveId = providerIds.firstOrNull { it == configuredId } ?: providerIds.firstOrNull()
    return SpeechProviderSelection(
        effectiveId = effectiveId,
        isFallback = configuredId != null && effectiveId != null && configuredId != effectiveId,
    )
}

/** Keep the seeded Android system voice from accidental deletion. */
internal fun canRemoveTtsSpeechProvider(id: Uuid): Boolean = id != DEFAULT_SYSTEM_TTS_ID
