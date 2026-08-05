package me.rerere.rikkahub.ui.pages.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import kotlin.uuid.Uuid

class PromptVM(
    private val settingsStore: SettingsStore
) : ViewModel() {
    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings())

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun deleteModeInjection(id: Uuid) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    modeInjections = settings.modeInjections.filterNot { it.id == id },
                    assistants = settings.assistants.map { assistant ->
                        assistant.copy(
                            modeInjectionIds = assistant.modeInjectionIds.filter { it != id }.toSet()
                        )
                    },
                )
            }
        }
    }

    fun deleteLorebook(id: Uuid) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    lorebooks = settings.lorebooks.filterNot { it.id == id },
                    assistants = settings.assistants.map { assistant ->
                        assistant.copy(
                            lorebookIds = assistant.lorebookIds.filter { it != id }.toSet()
                        )
                    },
                )
            }
        }
    }
}
