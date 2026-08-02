package me.rerere.rikkahub.ui.pages.modelmanager

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.STABLE_DIFFUSION_PROVIDER_ID
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences
import me.rerere.locallm.ModelInstall
import me.rerere.locallm.SdCatalog
import me.rerere.locallm.SdCatalogEntry
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FileUtils
import okhttp3.OkHttpClient

data class Progress(val percent: Int, val bytesRead: Long, val totalBytes: Long?)

class ModelManagerViewModel(
    private val context: Context,
    private val prefs: LocalRuntimePreferences,
    private val httpClient: OkHttpClient,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val runtime = LocalRuntime.StableDiffusion

    val catalogEntries: List<SdCatalogEntry> = SdCatalog.ENTRIES

    private val _downloadProgress = MutableStateFlow<Progress?>(null)
    val downloadProgress: StateFlow<Progress?> = _downloadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val provider: StateFlow<ProviderSetting.StableDiffusion?> = settingsStore.settingsFlow
        .map { s ->
            s.providers.firstOrNull { it.id == STABLE_DIFFUSION_PROVIDER_ID } as? ProviderSetting.StableDiffusion
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { refreshFromDisk() }
    }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        updateMyProvider { (it as? ProviderSetting.StableDiffusion)?.copy(enabled = enabled) ?: it }
    }

    fun startDefaultDownload() = viewModelScope.launch {
        val entry = SdCatalog.ENTRIES.firstOrNull { it.recommended } ?: return@launch
        executeDownload(entry.resolveUrl())
    }

    fun startManualDownload(url: String) = viewModelScope.launch {
        val normalized = ModelInstall.normalizeHuggingFaceUrl(url)
        if (!ModelInstall.isValidDownloadUrl(normalized)) {
            _errorMessage.value = "Invalid URL: must be https and well-formed"
            return@launch
        }
        executeDownload(normalized)
    }

    fun importModelFromUri(uri: Uri) = viewModelScope.launch {
        try {
            val displayName = withContext(Dispatchers.IO) {
                FileUtils.getFileNameFromUri(context, uri) ?: "model_${System.currentTimeMillis()}"
            }
            val safeName = if (displayName.endsWith(".gguf")) displayName else "$displayName.gguf"
            val target = ModelInstall.targetFile(ModelInstall.localModelsDir(context), runtime, safeName)
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Cannot open selected file")
            }
            val buf = ByteArray(16)
            target.inputStream().use { it.read(buf) }
            if (!ModelInstall.isValidMagicForExtension("gguf", buf)) {
                target.delete()
                _errorMessage.value = "Invalid or corrupted model file"
                return@launch
            }
            prefs.addInstalledModel(runtime, safeName, target.absolutePath)
            registerModel(safeName)
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Import failed"
        }
    }

    fun deleteModel(fileName: String) = viewModelScope.launch {
        val path = prefs.installedModels(runtime)[fileName]
        if (path != null) {
            File(path).delete()
            File("$path.partial").delete()
            prefs.removeInstalledModel(runtime, fileName)
        }
        updateMyProvider { p ->
            val m = p.models.firstOrNull { it.modelId == fileName }
            if (m != null) p.delModel(m) else p
        }
    }

    fun renameModel(modelId: String, newDisplayName: String) = viewModelScope.launch {
        if (newDisplayName.isBlank()) return@launch
        updateMyProvider { p ->
            val cur = p.models.firstOrNull { it.modelId == modelId } ?: return@updateMyProvider p
            p.editModel(cur.copy(displayName = newDisplayName.trim()))
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private suspend fun updateMyProvider(transform: (ProviderSetting) -> ProviderSetting) {
        settingsStore.update { settings ->
            settings.copy(
                providers = settings.providers.map {
                    if (it.id == STABLE_DIFFUSION_PROVIDER_ID) transform(it) else it
                },
            )
        }
    }

    private suspend fun executeDownload(url: String) {
        val fileName = ModelInstall.extractFileNameFromUrl(url)
        val target = ModelInstall.targetFile(ModelInstall.localModelsDir(context), runtime, fileName)
        try {
            collectDownloadProgress(url, fileName, target)
        } catch (e: CancellationException) {
            _downloadProgress.value = null
            throw e
        } catch (e: Throwable) {
            Log.w("ModelManagerVM", "Download failed", e)
            _downloadProgress.value = null
            _errorMessage.value = "Download failed: ${e::class.simpleName}: ${e.message ?: ""}"
        }
    }

    private suspend fun collectDownloadProgress(url: String, fileName: String, target: File) {
        ModelInstall.download(httpClient, url, target).collect { p ->
            when (p) {
                is ModelInstall.Progress.Started -> _downloadProgress.value = Progress(0, 0, p.totalBytes)
                is ModelInstall.Progress.Tick -> {
                    val total = p.totalBytes
                    val pct = if (total != null && total > 0) {
                        (p.bytesRead * 100 / total).toInt()
                    } else {
                        0
                    }
                    _downloadProgress.value = Progress(pct, p.bytesRead, total)
                }
                is ModelInstall.Progress.Done -> {
                    _downloadProgress.value = null
                    prefs.addInstalledModel(runtime, fileName, p.file.absolutePath)
                    registerModel(fileName)
                }
                is ModelInstall.Progress.Failed -> {
                    _downloadProgress.value = null
                    _errorMessage.value = p.cause.message.orEmpty()
                }
            }
        }
    }

    private suspend fun registerModel(fileName: String) {
        val model = Model(
            modelId = fileName,
            displayName = fileName,
            type = ModelType.IMAGE,
            inputModalities = listOf(Modality.TEXT),
            outputModalities = listOf(Modality.IMAGE),
        )
        updateMyProvider { it.addModel(model) }
    }

    private suspend fun refreshFromDisk() {
        val installed = prefs.installedModels(runtime)
        val broken = installed.filterValues { path ->
            val f = File(path)
            if (!f.exists()) {
                true
            } else {
                val buf = ByteArray(16)
                f.inputStream().use { it.read(buf) }
                !ModelInstall.isValidMagicForExtension("gguf", buf)
            }
        }
        if (broken.isNotEmpty()) {
            broken.forEach { (fileName, path) ->
                File(path).delete()
                prefs.removeInstalledModel(runtime, fileName)
                updateMyProvider { p ->
                    val m = p.models.firstOrNull { it.modelId == fileName }
                    if (m != null) p.delModel(m) else p
                }
            }
            _errorMessage.value = "Removed ${broken.size} broken model file(s)"
        } else {
            installed.keys.forEach { fileName ->
                if (provider.value?.models?.none { it.modelId == fileName } != false) {
                    registerModel(fileName)
                }
            }
        }
    }
}
