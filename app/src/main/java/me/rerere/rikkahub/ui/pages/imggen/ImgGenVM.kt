package me.rerere.rikkahub.ui.pages.imggen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.rerere.ai.provider.ImageCapabilities
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.imageCapabilities
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.ai.GenerationProgress
import me.rerere.rikkahub.data.ai.StableDiffusionBridge
import me.rerere.rikkahub.data.ai.generation.GenerationResult
import me.rerere.rikkahub.data.ai.tools.ToolInvocationContext
import me.rerere.rikkahub.data.ai.tools.image.MediaInputResolver
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.media.MediaArtifactRef
import me.rerere.rikkahub.data.media.writePayloadToFile
import me.rerere.rikkahub.data.modelregistry.ModelRegistry
import me.rerere.rikkahub.data.modelregistry.ModelResolution
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelRoleResolver
import me.rerere.rikkahub.data.modelregistry.ModelSourcePolicy
import me.rerere.rikkahub.data.repository.GenMediaRepository
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

@Serializable
data class GeneratedImage(
    val id: Int,
    val prompt: String,
    val filePath: String,
    val timestamp: Long,
    val model: String
)

private fun GenMediaEntity.toGeneratedImage(filesManager: FilesManager): GeneratedImage {
    val imagesDir = filesManager.getImagesDir()
    val fullPath = File(imagesDir, this.path.removePrefix("images/")).absolutePath

    return GeneratedImage(
        id = this.id,
        prompt = this.prompt,
        filePath = fullPath,
        timestamp = this.createAt,
        model = this.modelId
    )
}

class ImgGenVM(
    context: Application,
    val settingsStore: SettingsStore,
    val genMediaRepository: GenMediaRepository,
    private val filesManager: FilesManager,
    private val modelRoleResolver: ModelRoleResolver,
    private val modelRegistry: ModelRegistry,
    private val generationService: me.rerere.rikkahub.data.ai.generation.GenerationService,
    private val mediaInputResolver: MediaInputResolver,
) : AndroidViewModel(context) {
    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt

    private val _numberOfImages = MutableStateFlow(1)
    val numberOfImages: StateFlow<Int> = _numberOfImages

    private val _aspectRatio = MutableStateFlow(ImageAspectRatio.SQUARE)
    val aspectRatio: StateFlow<ImageAspectRatio> = _aspectRatio

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating
    private var cancelJob: Job? = null

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentGeneratedImages = MutableStateFlow<List<GeneratedImage>>(emptyList())
    val currentGeneratedImages: StateFlow<List<GeneratedImage>> = _currentGeneratedImages

    private val _referenceImages = MutableStateFlow<List<String>>(emptyList())
    val referenceImages: StateFlow<List<String>> = _referenceImages
    private val initialReferenceLoader = InitialImageReferenceLoader(context.appTempFolder)

    val generationProgress: StateFlow<GenerationProgress?> = StableDiffusionBridge.progress

    private val _imageCapabilities = MutableStateFlow(ProviderSetting.StableDiffusion().imageCapabilities)
    val imageCapabilities: StateFlow<ImageCapabilities> = _imageCapabilities

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                val resolved = modelRoleResolver.resolve(
                    ModelRole.IMAGE_GENERATION,
                    settings.getCurrentAssistant(),
                    settings,
                    ModelSourcePolicy.ANY,
                )
                val providerId = when (resolved) {
                    is ModelResolution.Resolved -> {
                        val aiModel = runCatching {
                            settings.findModelById(Uuid.parse(resolved.model.id))
                        }.getOrNull()
                        aiModel?.findProvider(settings.providers)?.id
                    }
                    else -> null
                }
                val setting = settings.providers.find { it.id == providerId }
                _imageCapabilities.value = setting?.imageCapabilities ?: ProviderSetting.StableDiffusion().imageCapabilities
            }
        }
    }

    val pager = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = { genMediaRepository.getAllMedia() }
    )
    val generatedImages: Flow<PagingData<GeneratedImage>> = pager.flow
        .map { pagingData ->
            pagingData.map { entity -> entity.toGeneratedImage(filesManager) }
        }
        .cachedIn(viewModelScope)

    fun updatePrompt(prompt: String) {
        _prompt.value = prompt
    }

    fun updateNumberOfImages(count: Int) {
        _numberOfImages.value = count.coerceIn(1, _imageCapabilities.value.maxOutputs.coerceAtLeast(1))
    }

    fun updateAspectRatio(aspectRatio: ImageAspectRatio) {
        _aspectRatio.value = aspectRatio
    }

    fun addReferenceImages(paths: List<String>) {
        _referenceImages.value = (_referenceImages.value + paths).distinct().take(MAX_REFERENCE_IMAGES)
    }

    fun initializeReferenceImage(imageRef: String?) {
        if (imageRef.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                val media = mediaInputResolver.resolveImage(imageRef, ToolInvocationContext.EMPTY)
                val stagedPath = withContext(Dispatchers.IO) {
                    initialReferenceLoader.stage(imageRef, media)
                }
                if (stagedPath != null) addReferenceImages(listOf(stagedPath))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize image reference", e)
                _error.value = e.message ?: "Failed to load image reference"
            }
        }
    }

    fun removeReferenceImage(path: String) {
        _referenceImages.value = _referenceImages.value.filterNot { it == path }
        deleteReferenceFiles(listOf(path))
    }

    fun clearReferenceImages() {
        deleteReferenceFiles(_referenceImages.value)
        _referenceImages.value = emptyList()
    }

    fun clearError() {
        _error.value = null
    }

    fun startNewSession() {
        cancelJob?.cancel()
        clearReferenceImages()
        _prompt.value = ""
        _currentGeneratedImages.value = emptyList()
        _error.value = null
        _isGenerating.value = false
    }

    fun generateImage() {
        if(prompt.value.isBlank()) return
        cancelJob?.cancel()
        cancelJob = viewModelScope.launch {
            try {
                _isGenerating.value = true
                _error.value = null
                _currentGeneratedImages.value = emptyList()
                StableDiffusionBridge.resetProgress()

                val settings = settingsStore.settingsFlow.first()
                val model = resolveModel(settings, ModelRole.IMAGE_GENERATION)
                    ?: throw IllegalStateException("No model selected")

                val requestPrompt = _prompt.value
                val params = ImageGenerationParams(
                    model = model,
                    prompt = requestPrompt,
                    numOfImages = _numberOfImages.value,
                    aspectRatio = _aspectRatio.value,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies
                )

                var previewFile: File? = null
                val outcome = generationService.generate(
                    settings = settings,
                    assistant = settings.getCurrentAssistant(),
                    params = params,
                    onPartial = { item ->
                        previewFile?.delete()
                        val file = saveImagePreview(item, model.displayName, item.partialImageIndex ?: 0)
                        previewFile = file
                        _currentGeneratedImages.value = listOf(
                            GeneratedImage(
                                id = 0,
                                prompt = requestPrompt,
                                filePath = file.absolutePath,
                                timestamp = System.currentTimeMillis(),
                                model = model.displayName,
                            )
                        )
                    },
                )
                previewFile?.delete()

                when (outcome) {
                    is GenerationResult.Empty -> _error.value = "Provider returned no images"
                    is GenerationResult.Success -> {
                        _currentGeneratedImages.value = outcome.artifacts.map {
                            GeneratedImage(
                                id = it.galleryId,
                                prompt = outcome.prompt,
                                filePath = it.path,
                                timestamp = System.currentTimeMillis(),
                                model = outcome.modelName,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if(e is CancellationException) return@launch
                Log.e(TAG, "Failed to generate image", e)
                _error.value = e.message ?: "Unknown error occurred"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun editImage() {
        if (prompt.value.isBlank() || referenceImages.value.isEmpty()) return
        cancelJob?.cancel()
        cancelJob = viewModelScope.launch {
            try {
                _isGenerating.value = true
                _error.value = null
                _currentGeneratedImages.value = emptyList()

                val settings = settingsStore.settingsFlow.first()
                val model = resolveModel(settings, ModelRole.IMAGE_EDITING)
                    ?: throw IllegalStateException("No model selected")

                val requestPrompt = _prompt.value
                val sourceImages = _referenceImages.value
                val params = ImageEditParams(
                    model = model,
                    prompt = requestPrompt,
                    images = sourceImages,
                    numOfImages = _numberOfImages.value,
                    aspectRatio = _aspectRatio.value,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies
                )

                var previewFile: File? = null
                val outcome = generationService.edit(
                    settings = settings,
                    assistant = settings.getCurrentAssistant(),
                    params = params,
                    sourceArtifacts = sourceImages.map { MediaArtifactRef(artifactId = it, path = it) },
                    onPartial = { item ->
                        previewFile?.delete()
                        val file = saveImagePreview(item, model.displayName, item.partialImageIndex ?: 0)
                        previewFile = file
                        _currentGeneratedImages.value = listOf(
                            GeneratedImage(
                                id = 0,
                                prompt = requestPrompt,
                                filePath = file.absolutePath,
                                timestamp = System.currentTimeMillis(),
                                model = model.displayName,
                            )
                        )
                    },
                )
                previewFile?.delete()

                when (outcome) {
                    is GenerationResult.Empty -> _error.value = "Provider returned no images"
                    is GenerationResult.Success -> {
                        _currentGeneratedImages.value = outcome.artifacts.map {
                            GeneratedImage(
                                id = it.galleryId,
                                prompt = outcome.prompt,
                                filePath = it.path,
                                timestamp = System.currentTimeMillis(),
                                model = outcome.modelName,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                Log.e(TAG, "Failed to edit image", e)
                _error.value = e.message ?: "Unknown error occurred"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun cancelGeneration() {
        cancelJob?.cancel()
    }

    fun registryModels() = modelRegistry.models

    private fun resolveModel(settings: me.rerere.rikkahub.data.datastore.Settings, role: ModelRole): me.rerere.ai.provider.Model? {
        val assistant = settings.getCurrentAssistant()
        val resolved = modelRoleResolver.resolve(role, assistant, settings, ModelSourcePolicy.ANY)
        val id = when (resolved) {
            is ModelResolution.Resolved -> resolved.model.id
            else -> settings.imageGenerationModelId.toString()
        }
        return runCatching { settings.findModelById(Uuid.parse(id)) }.getOrNull()
    }

    fun deleteImage(image: GeneratedImage) {
        viewModelScope.launch {
            try {
                // Delete from database first
                genMediaRepository.deleteMedia(image.id)

                // Then delete the file
                val file = File(image.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete image", e)
                _error.value = "Failed to delete image"
            }
        }
    }

    private fun deleteReferenceFiles(paths: List<String>) {
        viewModelScope.launch {
            paths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    /** Writes a partial item to a temp preview file for the progressive-generation UI. */
    private fun saveImagePreview(item: ImageGenerationItem, modelName: String, index: Int): File {
        val timestamp = System.currentTimeMillis()
        val imageFile = File(
            getApplication<Application>().appTempFolder,
            "imggen_${timestamp}_${modelName}_$index.png"
        )
        writePayloadToFile(item.payload, imageFile)
        return imageFile
    }

    companion object {
        private const val TAG = "ImgGenVM"
        private const val MAX_REFERENCE_IMAGES = 16
    }
}
