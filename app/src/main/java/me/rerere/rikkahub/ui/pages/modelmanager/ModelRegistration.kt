package me.rerere.rikkahub.ui.pages.modelmanager

import java.io.File
import kotlin.uuid.Uuid
import me.rerere.ai.provider.LITERT_PROVIDER_ID
import me.rerere.ai.provider.LLAMACPP_PROVIDER_ID
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.STABLE_DIFFUSION_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.Settings

object ModelRegistration {
    fun register(
        settings: Settings,
        providerId: Uuid,
        model: Model,
        absolutePath: String? = null,
    ): Settings {
        val existing = settings.providers.firstOrNull { it.id == providerId }
        val provider = when (providerId) {
            LITERT_PROVIDER_ID -> (existing as? ProviderSetting.LiteRtLocal
                ?: ProviderSetting.LiteRtLocal()).copy(
                enabled = true,
                models = upsertModel(existing?.models.orEmpty(), model),
            )
            LLAMACPP_PROVIDER_ID -> (existing as? ProviderSetting.LlamaCppLocal
                ?: ProviderSetting.LlamaCppLocal()).copy(
                enabled = true,
                models = upsertModel(existing?.models.orEmpty(), model),
            )
            STABLE_DIFFUSION_PROVIDER_ID -> {
                val current = (existing as? ProviderSetting.StableDiffusion
                    ?: ProviderSetting.StableDiffusion())
                current.copy(
                    enabled = true,
                    models = upsertModel(current.models, model),
                    currentModelPath = current.currentModelPath
                        ?.takeIf { File(it).isFile }
                        ?: absolutePath,
                )
            }
            else -> error("Unsupported local model provider: $providerId")
        }
        return settings.copy(
            providers = if (existing == null) {
                settings.providers + provider
            } else {
                settings.providers.map { if (it.id == providerId) provider else it }
            },
        )
    }

    private fun upsertModel(existing: List<Model>, model: Model): List<Model> =
        if (existing.any { it.modelId == model.modelId }) {
            existing.map { current ->
                if (current.modelId == model.modelId) {
                    model.copy(id = current.id, displayName = current.displayName)
                } else {
                    current
                }
            }
        } else {
            existing + model
        }
}
