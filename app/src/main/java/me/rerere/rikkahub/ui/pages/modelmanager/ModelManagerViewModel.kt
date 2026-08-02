package me.rerere.rikkahub.ui.pages.modelmanager

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.locallm.ImageProfile
import me.rerere.locallm.ImageProfileStore
import me.rerere.locallm.ModelEntry
import me.rerere.locallm.ModelInventory
import me.rerere.locallm.SdCatalog
import me.rerere.locallm.SdCatalogEntry

class ModelManagerViewModel : ViewModel() {
    private val inventory = ModelInventory()
    private val profileStore = ImageProfileStore()

    private val _installedModels = MutableStateFlow<List<ModelEntry>>(emptyList())
    val installedModels: StateFlow<List<ModelEntry>> = _installedModels.asStateFlow()

    private val _profiles = MutableStateFlow<List<ImageProfile>>(emptyList())
    val profiles: StateFlow<List<ImageProfile>> = _profiles.asStateFlow()

    val catalogEntries: List<SdCatalogEntry> = SdCatalog.ENTRIES

    init {
        refresh()
    }

    fun deleteModel(entry: ModelEntry) {
        // TODO: check for linked profiles, prompt cascade
        inventory.remove(entry.id)
        refresh()
    }

    fun deleteProfile(profile: ImageProfile) {
        profileStore.delete(profile.id)
        refresh()
    }

    private fun refresh() {
        _installedModels.value = inventory.list()
        _profiles.value = profileStore.list()
    }
}
