package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * App-side message reading behavior for one configured TTS provider instance.
 * Engine-side speech parameters remain on each TTSProviderSetting subtype.
 */
@Serializable
data class TtsPlaybackPreferences(
    val quotedOnly: Boolean = false,
    val skipBracketed: Boolean = false,
    val autoPlay: Boolean = false,
)

/** Older installations saved these three flags globally. Treat them as an
 * inherited baseline for providers that have not yet been customized. */
fun DisplaySetting.inheritedTtsPlayback(): TtsPlaybackPreferences = TtsPlaybackPreferences(
    quotedOnly = ttsOnlyReadQuoted,
    skipBracketed = ttsOnlyReadOutsideBrackets,
    autoPlay = autoPlayTTSAfterGeneration,
)

fun DisplaySetting.playbackFor(providerId: Uuid): TtsPlaybackPreferences =
    ttsPlaybackProfiles[providerId.toString()] ?: inheritedTtsPlayback()

fun DisplaySetting.withPlaybackFor(
    providerId: Uuid,
    preferences: TtsPlaybackPreferences,
): DisplaySetting = copy(
    ttsPlaybackProfiles = ttsPlaybackProfiles + (providerId.toString() to preferences),
)

fun Settings.selectedTtsPlayback(): TtsPlaybackPreferences =
    displaySetting.playbackFor(getSelectedTTSProvider()?.id ?: selectedTTSProviderId)
