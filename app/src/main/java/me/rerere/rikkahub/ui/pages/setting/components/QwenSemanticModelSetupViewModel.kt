package me.rerere.rikkahub.ui.pages.setting.components

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.search.SearchServiceOptions
import okhttp3.OkHttpClient

data class QwenSetupOperation(
    val kind: QwenSemanticModelManager.ModelKind,
    val completedFiles: Int,
    val totalFiles: Int,
    val percent: Int,
)

internal fun updateQwenModelDirectory(
    settings: Settings,
    kind: QwenSemanticModelManager.ModelKind,
    directory: File,
): Settings = settings.copy(
    searchServices = settings.searchServices.map { option ->
        when {
            kind == QwenSemanticModelManager.ModelKind.Embedder &&
                option is SearchServiceOptions.QwenEmbedderOptions ->
                option.copy(modelDir = directory.absolutePath)
            kind == QwenSemanticModelManager.ModelKind.Reranker &&
                option is SearchServiceOptions.QwenRerankerOptions ->
                option.copy(modelDir = directory.absolutePath)
            else -> option
        }
    },
)

class QwenSemanticModelSetupViewModel(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val httpClient: OkHttpClient,
) : ViewModel() {
    private val _embedderStatus = MutableStateFlow<QwenSemanticModelManager.ModelStatus>(
        QwenSemanticModelManager.ModelStatus.NotInstalled
    )
    private val _rerankerStatus = MutableStateFlow<QwenSemanticModelManager.ModelStatus>(
        QwenSemanticModelManager.ModelStatus.NotInstalled
    )
    private val _activeOperation = MutableStateFlow<QwenSetupOperation?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val embedderStatus: StateFlow<QwenSemanticModelManager.ModelStatus> = _embedderStatus.asStateFlow()
    val rerankerStatus: StateFlow<QwenSemanticModelManager.ModelStatus> = _rerankerStatus.asStateFlow()
    val activeOperation: StateFlow<QwenSetupOperation?> = _activeOperation.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun refresh(settings: Settings) {
        _embedderStatus.value = statusFor(settings, QwenSemanticModelManager.ModelKind.Embedder)
        _rerankerStatus.value = statusFor(settings, QwenSemanticModelManager.ModelKind.Reranker)
    }

    fun download(kind: QwenSemanticModelManager.ModelKind) {
        runOperation(kind) {
            QwenSemanticModelManager.downloadBundle(
                context = context,
                client = httpClient,
                kind = kind,
                onFileDone = { _, index, total ->
                    _activeOperation.value = QwenSetupOperation(kind, index + 1, total, 100)
                },
                onProgress = { percent ->
                    val operation = _activeOperation.value
                    _activeOperation.value = QwenSetupOperation(
                        kind = kind,
                        completedFiles = operation?.completedFiles ?: 0,
                        totalFiles = QwenSemanticModelManager.requiredFiles(kind).size,
                        percent = percent,
                    )
                },
            )
        }
    }

    fun chooseFolder(kind: QwenSemanticModelManager.ModelKind, uri: Uri) {
        runOperation(kind) {
            QwenSemanticModelManager.importBundleFromTree(context, kind, uri)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun runOperation(
        kind: QwenSemanticModelManager.ModelKind,
        operation: suspend () -> File,
    ) {
        if (_activeOperation.value != null) return
        viewModelScope.launch {
            _errorMessage.value = null
            _activeOperation.value = QwenSetupOperation(
                kind = kind,
                completedFiles = 0,
                totalFiles = QwenSemanticModelManager.requiredFiles(kind).size,
                percent = 0,
            )
            runCatching { operation() }
                .onSuccess { directory ->
                    updateModelDirectory(kind, directory)
                }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "Model setup failed"
                }
            _activeOperation.value = null
        }
    }

    private suspend fun updateModelDirectory(
        kind: QwenSemanticModelManager.ModelKind,
        directory: File,
    ) {
        settingsStore.update { settings -> updateQwenModelDirectory(settings, kind, directory) }
    }

    private fun statusFor(
        settings: Settings,
        kind: QwenSemanticModelManager.ModelKind,
    ): QwenSemanticModelManager.ModelStatus {
        val configuredPath = settings.searchServices.firstOrNull { option ->
            when (kind) {
                QwenSemanticModelManager.ModelKind.Embedder -> option is SearchServiceOptions.QwenEmbedderOptions
                QwenSemanticModelManager.ModelKind.Reranker -> option is SearchServiceOptions.QwenRerankerOptions
            }
        }?.let { option ->
            when (option) {
                is SearchServiceOptions.QwenEmbedderOptions -> option.modelDir
                is SearchServiceOptions.QwenRerankerOptions -> option.modelDir
                else -> ""
            }
        }.orEmpty()
        val directory = configuredPath.takeIf { it.isNotBlank() }?.let(::File)
            ?: QwenSemanticModelManager.modelDirectory(context, kind)
        return QwenSemanticModelManager.validate(directory, kind)
    }
}
