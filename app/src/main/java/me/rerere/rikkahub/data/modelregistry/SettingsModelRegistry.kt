package me.rerere.rikkahub.data.modelregistry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences
import me.rerere.rikkahub.data.datastore.SettingsStore
import kotlin.uuid.Uuid

class SettingsModelRegistry(
    private val settingsStore: SettingsStore,
    private val localPreferences: LocalRuntimePreferences,
    scope: CoroutineScope,
    private val providerManager: ProviderManager,
) : ModelRegistry {
    private val _models = MutableStateFlow<List<ModelDescriptor>>(emptyList())
    private val _providers = MutableStateFlow<List<ModelProviderDescriptor>>(emptyList())
    private val _assignments = MutableStateFlow(ModelAssignments())

    override val models: StateFlow<List<ModelDescriptor>> = _models.asStateFlow()
    override val providers: StateFlow<List<ModelProviderDescriptor>> = _providers.asStateFlow()
    override val assignments: StateFlow<ModelAssignments> = _assignments.asStateFlow()

    init {
        scope.launch {
            combine(
                settingsStore.settingsFlow,
                localPreferences.installedModelsFlow(LocalRuntime.LiteRT),
                localPreferences.installedModelsFlow(LocalRuntime.StableDiffusion),
            ) { settings, liteRt, stableDiffusion ->
                val localFiles = mapOf(
                    LocalRuntime.LiteRT to liteRt,
                    LocalRuntime.StableDiffusion to stableDiffusion,
                )
                val providers = settings.providers.map { provider ->
                    ModelProviderDescriptor(
                        id = provider.id.toString(),
                        displayName = provider.name,
                        enabled = provider.enabled,
                        modelIds = provider.models.map { modelId(it) },
                    )
                }
                val providerDescriptors = settings.providers.flatMap { provider ->
                    provider.models.map { model ->
                        descriptor(provider, model, localFiles, settings.disabledModelCapabilities[modelId(model)].orEmpty())
                    }
                }
                val knownLocalFiles = providerDescriptors
                    .mapNotNull { descriptor ->
                        (descriptor.source as? ModelSource.Local)?.files?.firstOrNull()
                    }
                    .toSet()
                val inventoryDescriptors = localFiles.flatMap { (runtime, files) ->
                    files.mapNotNull { (fileName, path) ->
                        if (fileName in knownLocalFiles) return@mapNotNull null
                        ModelDescriptor(
                            id = "local:$runtime:$fileName",
                            displayName = fileName,
                            source = ModelSource.Local(runtime, listOf(fileName)),
                            capabilities = emptySet(),
                            lifecycle = if (File(path).exists()) {
                                ModelLifecycle.READY
                            } else {
                                ModelLifecycle.ERROR
                            },
                            installed = true,
                            metadata = mapOf("path" to path),
                        )
                    }
                }
                val descriptors = providerDescriptors + inventoryDescriptors
                RegistrySnapshot(descriptors, providers, assignmentsFrom(settings))
            }.collect { snapshot ->
                _models.value = snapshot.models
                _providers.value = snapshot.providers
                _assignments.value = snapshot.assignments
            }
        }
    }

    override suspend fun refreshProvider(providerId: String) {
        val provider = settingsStore.settingsFlow.first().providers.firstOrNull {
            it.id.toString() == providerId
        } ?: error("Unknown provider: $providerId")
        val models = providerManager.getProviderByType(provider).listModels(provider)
        settingsStore.update { settings ->
            settings.copy(
                providers = settings.providers.map {
                    if (it.id == provider.id) it.copyProvider(models = models) else it
                },
            )
        }
    }

    override suspend fun setCapabilityEnabled(
        modelId: String,
        capability: ModelCapability,
        enabled: Boolean,
    ) {
        val model = _models.value.firstOrNull { it.id == modelId } ?: return
        require(capability in model.capabilities) { "Model $modelId does not advertise $capability" }
        settingsStore.update { settings ->
            val disabled = settings.disabledModelCapabilities.toMutableMap()
            val capabilities = (disabled[modelId].orEmpty()).toMutableSet()
            if (enabled) capabilities.remove(capability) else capabilities.add(capability)
            if (capabilities.isEmpty()) disabled.remove(modelId) else disabled[modelId] = capabilities
            settings.copy(disabledModelCapabilities = disabled)
        }
    }

    override suspend fun assign(role: ModelRole, modelId: String?) {
        val model = modelId?.let { id -> _models.value.firstOrNull { it.id == id } }
        if (modelId != null) {
            require(model != null) { "Unknown model: $modelId" }
            require(model.providerEnabled && model.supports(role.capability())) {
                "Model $modelId is not compatible with $role"
            }
            require(model.source !is ModelSource.Local || model.lifecycle == ModelLifecycle.READY) {
                "Local model $modelId is not ready"
            }
        }
        val modelUuid = modelId?.let {
            runCatching { Uuid.parse(it) }
                .getOrElse { error("Model $it has no persisted settings identity") }
        }
        settingsStore.update { settings ->
            when (role) {
                ModelRole.CHAT -> modelUuid?.let { settings.copy(chatModelId = it) }
                    ?: error("CHAT assignment cannot be cleared")
                ModelRole.VISION -> settings.copy(visionModelId = modelUuid)
                ModelRole.OCR -> modelUuid?.let { settings.copy(ocrModelId = it) }
                    ?: error("OCR assignment cannot be cleared")
                ModelRole.IMAGE_GENERATION -> modelUuid?.let { settings.copy(imageGenerationModelId = it) }
                    ?: error("IMAGE_GENERATION assignment cannot be cleared")
                ModelRole.IMAGE_EDITING -> settings.copy(imageEditingModelId = modelUuid)
                ModelRole.EMBEDDINGS -> settings.copy(
                    ragEmbeddingModel = (model?.source as? ModelSource.Cloud)?.remoteModelId
                        ?: error("EMBEDDINGS requires a cloud model")
                )
                ModelRole.TEXT_TO_SPEECH, ModelRole.SPEECH_TO_TEXT ->
                    error("$role assignment is not persisted by existing settings")
            }
        }
    }

    override suspend fun install(modelId: String) {
        error("Model installation remains owned by ModelManager")
    }

    override suspend fun rename(modelId: String, newDisplayName: String) {
        val name = newDisplayName.trim()
        if (name.isEmpty() || name.isBlank()) return
        settingsStore.update { settings ->
            settings.copy(
                providers = settings.providers.map { provider ->
                    val backing = provider.models.firstOrNull { it.id.toString() == modelId }
                        ?: return@map provider
                    provider.editModel(backing.copy(displayName = name))
                },
            )
        }
    }

    override suspend fun remove(modelId: String) {
        val model = _models.value.firstOrNull { it.id == modelId }
            ?: error("Unknown model: $modelId")
        val source = model.source as? ModelSource.Local
            ?: error("Cloud models cannot be removed from the local registry")
        val removedPaths = source.files.mapNotNull { localPreferences.installedModels(source.runtime)[it] }
        source.files.forEach { localPreferences.removeInstalledModel(source.runtime, it) }
        settingsStore.update { settings ->
            settings.copy(
                providers = settings.providers.map { provider ->
                    val backing = provider.models.firstOrNull { it.id.toString() == modelId }
                        ?: return@map provider
                    when (provider) {
                        is ProviderSetting.StableDiffusion -> provider.copy(
                            models = provider.models.filterNot { it.id.toString() == modelId },
                            currentModelPath = provider.currentModelPath
                                ?.takeUnless { path -> removedPaths.any { it == path } },
                        )
                        else -> provider.delModel(backing)
                    }
                },
            )
        }
        if (source.runtime == LocalRuntime.StableDiffusion) {
            me.rerere.rikkahub.data.ai.StableDiffusionBridge.invalidateSession()
        }
    }

    private fun descriptor(
        provider: ProviderSetting,
        model: Model,
        localFiles: Map<LocalRuntime, Map<String, String>>,
        disabledCapabilities: Set<ModelCapability>,
    ): ModelDescriptor {
        val runtime = when (provider) {
            is ProviderSetting.LiteRtLocal -> LocalRuntime.LiteRT
            is ProviderSetting.StableDiffusion -> LocalRuntime.StableDiffusion
            else -> null
        }
        val inferred = ModelCapabilityInference.infer(model)
        val files = runtime?.let { localFiles[it].orEmpty().keys.filter { file -> file == model.modelId } }.orEmpty()
        val installed = files.isNotEmpty()
        val capabilities = inferred.verified
        return ModelDescriptor(
            id = modelId(model),
            displayName = model.displayName.ifBlank { model.modelId },
            source = runtime?.let { ModelSource.Local(it, files) }
                ?: ModelSource.Cloud(provider.id.toString(), model.modelId),
            capabilities = capabilities,
            enabledCapabilities = capabilities - disabledCapabilities,
            lifecycle = if (runtime != null) {
                if (installed && files.all { file -> localFiles[runtime]?.get(file)?.let(::File)?.exists() == true }) {
                    ModelLifecycle.READY
                } else if (installed) {
                    ModelLifecycle.ERROR
                } else {
                    ModelLifecycle.AVAILABLE
                }
            } else ModelLifecycle.AVAILABLE,
            providerEnabled = provider.enabled,
            installed = installed,
            unverifiedCapabilities = inferred.unverified,
            metadata = buildMap {
                put("provider", provider.name)
                val path = runtime?.let { localFiles[it]?.get(model.modelId) }
                if (path != null) {
                    put("path", path)
                    put("sizeBytes", File(path).length().toString())
                }
            },
        )
    }

    private fun modelId(model: Model): String = model.id.toString()

    private fun assignmentsFrom(settings: me.rerere.rikkahub.data.datastore.Settings) = ModelAssignments(
        defaults = mapOf(
            ModelRole.CHAT to settings.chatModelId.toString(),
            ModelRole.VISION to settings.visionModelId?.toString(),
            ModelRole.OCR to settings.ocrModelId.toString(),
            ModelRole.IMAGE_GENERATION to settings.imageGenerationModelId.toString(),
            ModelRole.IMAGE_EDITING to settings.imageEditingModelId?.toString(),
            ModelRole.EMBEDDINGS to settings.ragEmbeddingModel,
        ),
        legacyDefaults = mapOf(
            "chat" to settings.chatModelId.toString(),
            "title" to settings.titleModelId?.toString(),
            "translation" to settings.translateModeId.toString(),
            "image_generation" to settings.imageGenerationModelId.toString(),
            "ocr" to settings.ocrModelId.toString(),
        ),
    )

    private data class RegistrySnapshot(
        val models: List<ModelDescriptor>,
        val providers: List<ModelProviderDescriptor>,
        val assignments: ModelAssignments,
    )
}
