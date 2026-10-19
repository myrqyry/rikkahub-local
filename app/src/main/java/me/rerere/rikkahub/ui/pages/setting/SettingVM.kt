package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
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

    fun updateTranslationThinkingBudget(budget: Int) {
        viewModelScope.launch {
            settingsStore.update { latest -> latest.copy(translateThinkingBudget = budget) }
        }
    }
}
