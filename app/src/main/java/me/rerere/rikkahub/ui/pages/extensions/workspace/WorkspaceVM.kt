package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.opencode.OpenCodeRepository
import me.rerere.rikkahub.data.opencode.OpenCodeProject
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.RootfsInstallProgress

class WorkspaceVM(
    private val repository: WorkspaceRepository,
    private val openCodeRepository: OpenCodeRepository,
) : ViewModel() {
    val workspaces = repository.listFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val openCodeWorkspaces = openCodeRepository.observeWorkspaceRows()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _openCodeProjects = MutableStateFlow<List<OpenCodeProject>>(emptyList())
    val openCodeProjects = _openCodeProjects.asStateFlow()

    private val _openCodeProjectError = MutableStateFlow<String?>(null)
    val openCodeProjectError = _openCodeProjectError.asStateFlow()

    fun create(name: String) {
        viewModelScope.launch {
            runCatching { repository.create(name) }
        }
    }

    fun rename(workspace: WorkspaceEntity, name: String) {
        viewModelScope.launch {
            runCatching { repository.rename(workspace.id, name) }
        }
    }

    fun delete(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            repository.delete(workspace.id)
        }
    }

    fun createOpenCodeConnection(name: String, baseUrl: String, credential: String?) {
        viewModelScope.launch {
            runCatching { openCodeRepository.createConnection(name, baseUrl, credential) }
        }
    }

    fun discoverOpenCodeProjects(connectionId: String) {
        viewModelScope.launch {
            _openCodeProjectError.value = null
            runCatching { openCodeRepository.testConnection(connectionId) }
                .onSuccess {
                    runCatching { openCodeRepository.discoverProjects(connectionId) }
                        .onSuccess { _openCodeProjects.value = it }
                        .onFailure { _openCodeProjectError.value = it.message ?: "Could not load OpenCode projects" }
                }
                .onFailure { _openCodeProjectError.value = it.message ?: "Could not connect to OpenCode" }
        }
    }

    fun selectOpenCodeProject(connectionId: String, project: OpenCodeProject) {
        viewModelScope.launch {
            runCatching { openCodeRepository.selectProject(connectionId, project) }
                .onSuccess {
                    _openCodeProjects.value = emptyList()
                    _openCodeProjectError.value = null
                }
                .onFailure { _openCodeProjectError.value = it.message ?: "Could not select OpenCode project" }
        }
    }

    fun clearOpenCodeProjects() {
        _openCodeProjects.value = emptyList()
        _openCodeProjectError.value = null
    }
}
