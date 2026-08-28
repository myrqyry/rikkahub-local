package me.rerere.locallm

import java.io.File
import kotlin.uuid.Uuid
import me.rerere.ai.provider.LITERT_PROVIDER_ID
import me.rerere.ai.provider.LLAMACPP_PROVIDER_ID
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.STABLE_DIFFUSION_PROVIDER_ID
import me.rerere.locallm.litert.image.FLUX2_KLEIN_MODEL

enum class ModelModality {
    CHAT,
    IMAGE,
    VISION_AUDIO,
}

enum class ModelInstallKind {
    SINGLE_FILE_LITERT,
    FLUX2_KLEIN_PACKAGE,
    GGUF,
}

data class ModelCatalogEntry(
    val model: Model,
    val modality: ModelModality,
    val runtime: LocalRuntime,
    val providerId: Uuid,
    val installKind: ModelInstallKind,
    val sourceUrl: String? = null,
)

data class ModelRoute(
    val runtime: LocalRuntime,
    val providerId: Uuid,
)

object ModelCatalog {
    val entries: List<ModelCatalogEntry> = listOf(
        ModelCatalogEntry(
            model = FLUX2_KLEIN_MODEL,
            modality = ModelModality.IMAGE,
            runtime = LocalRuntime.LiteRT,
            providerId = LITERT_PROVIDER_ID,
            installKind = ModelInstallKind.FLUX2_KLEIN_PACKAGE,
        ),
    )

    fun byModality(modality: ModelModality): List<ModelCatalogEntry> =
        entries.filter { it.modality == modality }
}

object ModelRouting {
    fun resolve(entry: ModelCatalogEntry, providerId: Uuid): ModelRoute {
        check(entry.providerId == providerId) {
            "Model ${entry.model.modelId} requires provider ${entry.providerId}"
        }
        return ModelRoute(entry.runtime, entry.providerId)
    }

    fun classifyGguf(file: File): ModelRoute =
        when (GgufClassifier.classifyFile(file)) {
            LocalRuntime.LlamaCpp -> ModelRoute(LocalRuntime.LlamaCpp, LLAMACPP_PROVIDER_ID)
            else -> ModelRoute(LocalRuntime.StableDiffusion, STABLE_DIFFUSION_PROVIDER_ID)
        }
}
