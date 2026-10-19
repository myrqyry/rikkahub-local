package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.TtsPlaybackPreferences
import me.rerere.rikkahub.data.datastore.withPlaybackFor
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.ai.prompts.ChatUtilityPrompt
import me.rerere.rikkahub.data.ai.prompts.withUtilityPrompt
import me.rerere.rikkahub.data.ai.mcp.McpManager

class SettingVM(
    private val settingsStore: SettingsStore,
    private val mcpManager: McpManager
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(providers = emptyList()))

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    /** Make a targeted change against the latest settings, not a stale screen snapshot. */
    fun updateUtilityPrompt(kind: ChatUtilityPrompt, value: String) {
        viewModelScope.launch {
            settingsStore.update { latest -> latest.withUtilityPrompt(kind, value) }
        }
    }

    /** Save the provider and its playback profile in one serialized update.
     * No stale screen copy can overwrite unrelated providers or other settings. */
    fun updateTtsProvider(
        originalId: Uuid,
        provider: TTSProviderSetting,
        playback: TtsPlaybackPreferences,
    ) {
        viewModelScope.launch {
            settingsStore.update { latest ->
                if (provider.id != originalId || latest.ttsProviders.none { it.id == originalId }) {
                    latest
                } else {
                    latest.copy(
                        ttsProviders = latest.ttsProviders.map {
                            if (it.id == originalId) provider else it
                        },
                        displaySetting = latest.displaySetting.withPlaybackFor(originalId, playback),
                    )
                }
            }
        }
    }

    fun addTtsProvider(provider: TTSProviderSetting, playback: TtsPlaybackPreferences) {
        viewModelScope.launch {
            settingsStore.update { latest ->
                if (latest.ttsProviders.any { it.id == provider.id }) latest
                else latest.copy(
                    ttsProviders = listOf(provider) + latest.ttsProviders,
                    displaySetting = latest.displaySetting.withPlaybackFor(provider.id, playback),
                )
            }
        }
    }

    /** Selection, removal and reordering are also targeted updates: the
     * provider cards must not re-save a stale full-settings snapshot. */
    fun selectTtsProvider(id: Uuid) {
        viewModelScope.launch {
            settingsStore.update { latest ->
                if (latest.ttsProviders.none { it.id == id }) latest
                else latest.copy(selectedTTSProviderId = id)
            }
        }
    }

    fun removeTtsProvider(id: Uuid) {
        viewModelScope.launch {
            settingsStore.update { latest ->
                if (latest.ttsProviders.none { it.id == id }) latest else {
                    val remaining = latest.ttsProviders.filterNot { it.id == id }
                    val selected = if (latest.selectedTTSProviderId == id) {
                        remaining.firstOrNull { it.id == me.rerere.rikkahub.data.datastore.DEFAULT_SYSTEM_TTS_ID }
                            ?.id ?: remaining.firstOrNull()?.id
                            ?: me.rerere.rikkahub.data.datastore.DEFAULT_SYSTEM_TTS_ID
                    } else latest.selectedTTSProviderId
                    latest.copy(
                        ttsProviders = remaining,
                        selectedTTSProviderId = selected,
                        displaySetting = latest.displaySetting.copy(
                            ttsPlaybackProfiles = latest.displaySetting.ttsPlaybackProfiles - id.toString(),
                        ),
                    )
                }
            }
        }
    }

    fun reorderTtsProviders(ids: List<Uuid>) {
        viewModelScope.launch {
            settingsStore.update { latest ->
                val ranks = ids.withIndex().associate { (index, id) -> id to index }
                latest.copy(ttsProviders = latest.ttsProviders.sortedWith(
                    compareBy { ranks[it.id] ?: Int.MAX_VALUE }
                ))
            }
        }
    }

    fun updateTranslationThinkingBudget(budget: Int) {
        viewModelScope.launch {
            settingsStore.update { latest -> latest.copy(translateThinkingBudget = budget) }
        }
    }
}
