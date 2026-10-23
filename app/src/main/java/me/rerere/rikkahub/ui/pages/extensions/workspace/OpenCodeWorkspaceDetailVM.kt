package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.opencode.OpenCodeMessage
import me.rerere.rikkahub.data.opencode.OpenCodePermission
import me.rerere.rikkahub.data.opencode.OpenCodePermissionReply
import me.rerere.rikkahub.data.opencode.OpenCodeRepository
import me.rerere.rikkahub.data.opencode.OpenCodeSession
import me.rerere.rikkahub.data.opencode.OpenCodeSessionSignal

data class OpenCodeWorkspaceDetailState(
    val session: OpenCodeSession? = null,
    val sessions: List<OpenCodeSession> = emptyList(),
    val messages: List<OpenCodeMessage> = emptyList(),
    val pendingPermissions: List<OpenCodePermission> = emptyList(),
    val status: String = "unknown",
    val eventConnected: Boolean = false,
    val permissionError: String? = null,
    val handlingPermissionId: String? = null,
    val loading: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
)

class OpenCodeWorkspaceDetailVM(
    private val refId: String,
    private val repository: OpenCodeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OpenCodeWorkspaceDetailState())
    val state = _state.asStateFlow()
    private var monitorJob: Job? = null
    private var snapshotJob: Job? = null
    private var pendingSnapshot = false

    init { loadExistingSessions() }

    fun loadExistingSessions() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val sessions = repository.listSessions(refId)
                _state.value = _state.value.copy(sessions = sessions, loading = false)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Could not list sessions")
            }
        }
    }

    fun createSession() {
        if (_state.value.loading || _state.value.sending) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val session = repository.createSession(refId)
                _state.value = _state.value.copy(loading = false, sessions = listOf(session) + _state.value.sessions)
                selectSession(session)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Could not create session")
            }
        }
    }

    fun selectSession(session: OpenCodeSession) {
        monitorJob?.cancel()
        snapshotJob?.cancel()
        pendingSnapshot = false
        _state.value = _state.value.copy(
            session = session, messages = emptyList(), pendingPermissions = emptyList(),
            status = "unknown", eventConnected = false, permissionError = null, error = null,
        )
        scheduleSnapshot(session.id, immediate = true)
        monitorJob = viewModelScope.launch {
            try {
                repository.observeSession(refId, session.id).collect { signal ->
                    if (_state.value.session?.id != session.id) return@collect
                    when (signal) {
                        OpenCodeSessionSignal.Connected -> {
                            _state.value = _state.value.copy(eventConnected = true)
                            scheduleSnapshot(session.id, immediate = true)
                        }
                        is OpenCodeSessionSignal.Disconnected ->
                            _state.value = _state.value.copy(eventConnected = false)
                        OpenCodeSessionSignal.MessagesChanged,
                        OpenCodeSessionSignal.StatusChanged,
                        OpenCodeSessionSignal.PermissionsChanged -> scheduleSnapshot(session.id)
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (_state.value.session?.id == session.id) {
                    _state.value = _state.value.copy(
                        eventConnected = false,
                        error = "OpenCode live updates unavailable: ${error.message ?: "connection failed"}",
                    )
                }
            }
        }
    }

    fun refresh() {
        _state.value.session?.let { scheduleSnapshot(it.id, immediate = true) }
            ?: loadExistingSessions()
    }

    private fun scheduleSnapshot(sessionId: String, immediate: Boolean = false) {
        if (snapshotJob?.isActive == true) {
            pendingSnapshot = true
            return
        }
        snapshotJob = viewModelScope.launch {
            do {
                pendingSnapshot = false
                if (!immediate) delay(300)
                refreshSnapshot(sessionId)
            } while (pendingSnapshot && _state.value.session?.id == sessionId)
        }
    }

    private suspend fun refreshSnapshot(sessionId: String) {
        try {
            val messages = repository.getMessages(refId, sessionId)
            if (_state.value.session?.id != sessionId) return
            _state.value = _state.value.copy(messages = messages, error = null)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (_state.value.session?.id == sessionId) {
                _state.value = _state.value.copy(error = error.message ?: "Could not load OpenCode messages")
            }
        }
        try {
            val status = repository.getSessionStatus(refId, sessionId) ?: "unknown"
            if (_state.value.session?.id == sessionId) _state.value = _state.value.copy(status = status)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (_state.value.session?.id == sessionId) _state.value = _state.value.copy(status = "unknown")
        }
        try {
            val permissions = repository.listPendingPermissions(refId, sessionId)
            if (_state.value.session?.id == sessionId) {
                _state.value = _state.value.copy(pendingPermissions = permissions, permissionError = null)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (_state.value.session?.id == sessionId) {
                _state.value = _state.value.copy(
                    pendingPermissions = emptyList(),
                    permissionError = "Pending approvals unavailable: ${error.message ?: "server error"}",
                )
            }
        }
    }

    fun send(text: String) {
        val session = _state.value.session ?: return
        val prompt = text.trim()
        if (prompt.isEmpty() || _state.value.sending) return
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true, error = null)
            try {
                repository.prompt(refId, session.id, prompt)
                scheduleSnapshot(session.id, immediate = true)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(
                    error = "Prompt outcome unconfirmed: ${error.message ?: "connection failed"}. " +
                        "Check session history before resending.",
                )
                scheduleSnapshot(session.id, immediate = true)
            } finally {
                _state.value = _state.value.copy(sending = false)
            }
        }
    }

    fun respondToPermission(id: String, reply: OpenCodePermissionReply) {
        val sessionId = _state.value.session?.id ?: return
        if (_state.value.pendingPermissions.none { it.id == id && it.sessionId == sessionId }) return
        if (_state.value.handlingPermissionId != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(handlingPermissionId = id, permissionError = null)
            try {
                if (!repository.replyPermission(refId, sessionId, id, reply)) {
                    _state.value = _state.value.copy(permissionError = "Request expired or was not accepted by OpenCode")
                }
                scheduleSnapshot(sessionId, immediate = true)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(permissionError = error.message ?: "Could not answer permission")
            } finally {
                _state.value = _state.value.copy(handlingPermissionId = null)
            }
        }
    }

    fun abort() {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            try {
                repository.abortSession(refId, session.id)
                scheduleSnapshot(session.id, immediate = true)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(error = error.message ?: "Could not abort session")
            }
        }
    }
}
