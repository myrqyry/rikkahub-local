package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.opencode.OpenCodeMessage
import me.rerere.rikkahub.data.opencode.OpenCodeRepository
import me.rerere.rikkahub.data.opencode.OpenCodeSession

data class OpenCodeWorkspaceDetailState(
    val session: OpenCodeSession? = null,
    val sessions: List<OpenCodeSession> = emptyList(),
    val messages: List<OpenCodeMessage> = emptyList(),
    val loading: Boolean = false,
    val sending: Boolean = false,
    val acknowledgedPrompt: String? = null,
    val acknowledgedRevision: Long = 0,
    val error: String? = null,
)

class OpenCodeWorkspaceDetailVM(
    private val refId: String,
    private val repository: OpenCodeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OpenCodeWorkspaceDetailState())
    val state = _state.asStateFlow()

    init { loadSessions() }

    fun loadSessions() {
        if (_state.value.loading || _state.value.sending) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val sessions = repository.listSessions(refId)
                _state.value = _state.value.copy(sessions = sessions, loading = false)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Could not load sessions")
            }
        }
    }

    fun selectSession(session: OpenCodeSession) {
        if (_state.value.loading || _state.value.sending || session !in _state.value.sessions) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val messages = repository.getMessages(refId, session.id)
                _state.value = _state.value.copy(session = session, messages = messages, loading = false)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Could not load session")
            }
        }
    }

    fun openSessionPicker() {
        if (_state.value.loading || _state.value.sending) return
        _state.value = _state.value.copy(session = null, messages = emptyList(), error = null)
        loadSessions()
    }

    fun createSession() {
        if (_state.value.loading || _state.value.sending) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val session = repository.createSession(refId)
                _state.value = _state.value.copy(
                    session = session,
                    sessions = listOf(session) + _state.value.sessions.filterNot { it.id == session.id },
                    messages = emptyList(),
                    loading = false,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Could not create session")
            }
        }
    }

    fun refresh() {
        val session = _state.value.session
        if (session == null) {
            loadSessions()
            return
        }
        if (_state.value.loading || _state.value.sending) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val messages = repository.getMessages(refId, session.id)
                _state.value = _state.value.copy(messages = messages, loading = false)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Could not load messages")
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
                // The request is acknowledged only after the server returns successfully.
                // Preserve the draft on ambiguous transport failures so it is not lost.
                _state.value = _state.value.copy(
                    acknowledgedPrompt = prompt,
                    acknowledgedRevision = _state.value.acknowledgedRevision + 1,
                )
                // Reload server-owned messages; synthetic local IDs duplicate durable
                // messages and obscure whether a prompt really reached the server.
                try {
                    val messages = repository.getMessages(refId, session.id)
                    _state.value = _state.value.copy(messages = messages, sending = false)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    _state.value = _state.value.copy(
                        sending = false,
                        error = "Message sent, but conversation refresh failed: ${error.message.orEmpty()}",
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(
                    sending = false,
                    error = "Could not confirm message delivery: ${error.message.orEmpty()}",
                )
            }
        }
    }

    fun abort() {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            runCatching { repository.abortSession(refId, session.id) }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        _state.value = _state.value.copy(error = error.message ?: "Could not abort session")
                    }
                }
        }
    }
}
