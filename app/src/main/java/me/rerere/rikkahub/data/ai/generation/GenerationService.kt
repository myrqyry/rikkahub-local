package me.rerere.rikkahub.data.ai.generation

import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.toList
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderSetting
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

class GenerationService(
    private val modelRoleResolver: ModelRoleResolver,
    private val backend: ImageToolBackend,
    private val mediaStore: ImageMediaStore,
) {
    data class GenerationOutcome(
        val artifacts: List<StoredImageArtifact>,
        val receipt: GenerationReceipt,
        val prompt: String,
        val modelName: String,
    )

    suspend fun generate(
        settings: Settings,
        assistant: Assistant,
        params: ImageGenerationParams,
        sourceArtifacts: List<MediaArtifactRef> = emptyList(),
    ): GenerationOutcome {
        val descriptor = resolveDescriptor(settings, assistant, ModelRole.IMAGE_GENERATION)
        val providerSetting = resolveProviderSetting(settings, descriptor)
        if (!assistant.canProcessImageWith(providerSetting)) {
            throw IllegalStateException("Cloud image processing is disabled for this assistant")
        }
        val startedAt = System.nanoTime()
        val items = backend.generateImage(providerSetting, params).toList()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        val finals = items.filter { !it.partial }
        val artifacts = finals.map {
            mediaStore.saveGenerated(it, params.prompt, descriptor, ImageOperation.IMAGE_GENERATION, sourceArtifacts)
        }
        val receipt = buildReceipt(artifacts.first(), providerSetting, descriptor.id, elapsedMs, sourceArtifacts)
        return GenerationOutcome(artifacts, receipt, params.prompt, descriptor.displayName)
    }

    suspend fun edit(
        settings: Settings,
        assistant: Assistant,
        params: ImageEditParams,
        sourceArtifacts: List<MediaArtifactRef>,
    ): GenerationOutcome {
        val descriptor = resolveDescriptor(settings, assistant, ModelRole.IMAGE_EDITING)
        val providerSetting = resolveProviderSetting(settings, descriptor)
        if (!assistant.canProcessImageWith(providerSetting)) {
            throw IllegalStateException("Cloud image processing is disabled for this assistant")
        }
        val startedAt = System.nanoTime()
        val items = backend.editImage(providerSetting, params).toList()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        val finals = items.filter { !it.partial }
        val artifacts = finals.map {
            mediaStore.saveGenerated(it, params.prompt, descriptor, ImageOperation.IMAGE_EDIT, sourceArtifacts)
        }
        val receipt = buildReceipt(artifacts.first(), providerSetting, descriptor.id, elapsedMs, sourceArtifacts)
        return GenerationOutcome(artifacts, receipt, params.prompt, descriptor.displayName)
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
