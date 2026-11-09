package me.rerere.rikkahub.data.datastore

import me.rerere.tts.provider.TTSProviderSetting

/**
 * Suggested TTS providers are first-run defaults, not undeletable built-ins.
 * A saved empty list is intentional and must never restore optional cloud providers.
 * Only the system voice is maintained as a permanent recovery option.
 */
internal fun restoreTtsDefaults(
    persisted: List<TTSProviderSetting>?,
    firstRunDefaults: List<TTSProviderSetting>,
    requiredSystem: TTSProviderSetting.SystemTTS,
): List<TTSProviderSetting> {
    val providers = persisted ?: firstRunDefaults.map { it.copyProvider() }
    return if (providers.any { it.id == requiredSystem.id }) providers
    else providers + requiredSystem.copyProvider()
}
