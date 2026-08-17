package me.rerere.rikkahub.data.ai.generation

import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.collect
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.rikkahub.data.ai.GenerationReceipt
import me.rerere.rikkahub.data.ai.tools.image.ImageOperation
import me.rerere.rikkahub.data.ai.tools.image.ImageTextExtractionResult
import me.rerere.rikkahub.data.ai.tools.image.ImageTextExtractor
import me.rerere.rikkahub.data.ai.tools.image.ImageToolBackend
import me.rerere.rikkahub.data.ai.tools.image.ResolvedMedia
import me.rerere.rikkahub.data.ai.tools.image.StoredImageArtifact
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.media.ImageMediaStore
import me.rerere.rikkahub.data.media.MediaArtifactRef
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelResolution
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelRoleResolver
import me.rerere.rikkahub.data.modelregistry.ModelSourcePolicy
import me.rerere.rikkahub.data.modelregistry.canProcessImageWith

/**
 * Typed outcome of a generation/edit run. [Success] carries persisted artifacts
 * plus a receipt; [Empty] means the provider produced no final images (callers
 * surface their own "no images" messaging instead of the service throwing).
 */
sealed interface GenerationResult {
    val prompt: String
    val modelName: String

    data class Success(
        val artifacts: List<StoredImageArtifact>,
        val receipt: GenerationReceipt,
        override val prompt: String,
        override val modelName: String,
    ) : GenerationResult

    data class Empty(
        override val prompt: String,
        override val modelName: String,
    ) : GenerationResult
}

class GenerationService(
    private val modelRoleResolver: ModelRoleResolver,
    private val backend: ImageToolBackend,
    private val mediaStore: ImageMediaStore,
) {

    suspend fun generate(
        settings: Settings,
        assistant: Assistant,
        params: ImageGenerationParams,
        sourceArtifacts: List<MediaArtifactRef> = emptyList(),
        onPartial: suspend (ImageGenerationItem) -> Unit = {},
    ): GenerationResult {
        val descriptor = resolveDescriptor(settings, assistant, ModelRole.IMAGE_GENERATION)
        val providerSetting = resolveProviderSetting(settings, descriptor)
        if (!assistant.canProcessImageWith(providerSetting)) {
            throw IllegalStateException("Cloud image processing is disabled for this assistant")
        }
        val startedAt = System.nanoTime()
        val finals = mutableListOf<ImageGenerationItem>()
        backend.generateImage(providerSetting, params).collect { item ->
            if (item.partial) onPartial(item) else finals += item
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        return persistFinals(
            finals = finals,
            prompt = params.prompt,
            descriptor = descriptor,
            operation = ImageOperation.IMAGE_GENERATION,
            providerSetting = providerSetting,
            elapsedMs = elapsedMs,
            sourceArtifacts = sourceArtifacts,
        )
    }

    suspend fun edit(
        settings: Settings,
        assistant: Assistant,
        params: ImageEditParams,
        sourceArtifacts: List<MediaArtifactRef>,
        onPartial: suspend (ImageGenerationItem) -> Unit = {},
    ): GenerationResult {
        val descriptor = resolveDescriptor(settings, assistant, ModelRole.IMAGE_EDITING)
        val providerSetting = resolveProviderSetting(settings, descriptor)
        if (!assistant.canProcessImageWith(providerSetting)) {
            throw IllegalStateException("Cloud image processing is disabled for this assistant")
        }
        val startedAt = System.nanoTime()
        val finals = mutableListOf<ImageGenerationItem>()
        backend.editImage(providerSetting, params).collect { item ->
            if (item.partial) onPartial(item) else finals += item
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        return persistFinals(
            finals = finals,
            prompt = params.prompt,
            descriptor = descriptor,
            operation = ImageOperation.IMAGE_EDIT,
            providerSetting = providerSetting,
            elapsedMs = elapsedMs,
            sourceArtifacts = sourceArtifacts,
        )
    }

    suspend fun analyze(
        settings: Settings,
        assistant: Assistant,
        media: ResolvedMedia,
        requireLocal: Boolean = false,
        timeoutMillis: Long = 60_000L,
    ): ImageTextExtractionResult = ImageTextExtractor(
        backend = backend,
        modelRoleResolver = modelRoleResolver,
    ).extract(media, assistant, settings, requireLocal, timeoutMillis)

    private suspend fun persistFinals(
        finals: List<ImageGenerationItem>,
        prompt: String,
        descriptor: ModelDescriptor,
        operation: ImageOperation,
        providerSetting: ProviderSetting,
        elapsedMs: Long,
        sourceArtifacts: List<MediaArtifactRef>,
    ): GenerationResult {
        if (finals.isEmpty()) return GenerationResult.Empty(prompt, descriptor.displayName)
        val artifacts = finals.map {
            mediaStore.saveGenerated(it, prompt, descriptor, operation, sourceArtifacts)
        }
        val receipt = buildReceipt(artifacts.first(), providerSetting, descriptor.id, elapsedMs, sourceArtifacts)
        return GenerationResult.Success(artifacts, receipt, prompt, descriptor.displayName)
    }

    private fun resolveDescriptor(
        settings: Settings,
        assistant: Assistant,
        role: ModelRole,
    ): ModelDescriptor {
        val resolved = modelRoleResolver.resolve(role, assistant, settings, ModelSourcePolicy.ANY)
        return (resolved as? ModelResolution.Resolved)?.model
            ?: throw IllegalStateException("No model selected for ${role.name}")
    }

    private fun resolveProviderSetting(
        settings: Settings,
        descriptor: ModelDescriptor,
    ): ProviderSetting {
        val model = settings.findModelById(Uuid.parse(descriptor.id))
            ?: throw IllegalStateException("Model not found")
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")
        return settings.providers.find { it.id == provider.id }
            ?: throw IllegalStateException("Provider setting not found")
    }

    private fun buildReceipt(
        artifact: StoredImageArtifact,
        providerSetting: ProviderSetting,
        modelId: String,
        elapsedMs: Long,
        sourceArtifacts: List<MediaArtifactRef>,
    ): GenerationReceipt {
        val sd = providerSetting as? ProviderSetting.StableDiffusion
        return GenerationReceipt(
            artifactId = artifact.artifactId,
            modelId = modelId,
            modelRevision = null,
            runtime = if (sd != null) "stable-diffusion.cpp" else "cloud",
            backend = when {
                sd == null -> "cloud"
                sd.useVulkan -> "VULKAN"
                else -> "CPU"
            },
            width = artifact.width,
            height = artifact.height,
            seed = sd?.seed?.toLong(),
            steps = sd?.steps,
            cfg = sd?.cfgScale,
            sampler = null,
            scheduler = null,
            elapsedMs = elapsedMs,
            sourceArtifacts = sourceArtifacts,
        )
    }
}
