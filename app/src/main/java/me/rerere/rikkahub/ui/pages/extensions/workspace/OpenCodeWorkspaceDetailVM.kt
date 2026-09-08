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
    val messages: List<OpenCodeMessage> = emptyList(),
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

    fun createSession() {
        if (_state.value.loading || _state.value.sending) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val session = repository.createSession(refId)
                _state.value = _state.value.copy(session = session, loading = false)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Could not create session")
            }
        }
    }

    fun refresh() {
        val session = _state.value.session ?: return
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
                val userMessage = OpenCodeMessage("local-${System.nanoTime()}", "user", prompt)
                val answer = repository.prompt(refId, session.id, prompt)
                _state.value = _state.value.copy(
                    messages = _state.value.messages + userMessage + answer,
                    sending = false,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(sending = false, error = error.message ?: "Prompt failed")
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
