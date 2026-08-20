package me.rerere.rikkahub.subagent

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting

/**
 * Resolves a `model_id` value passed to sub-agent dispatch against the CHAT-type models of
 * ENABLED providers. Lookup order: provider model id (case-insensitive), then display name
 * (case-insensitive exact). Embedding (non-CHAT) models are never eligible; a model belonging
 * to a disabled provider is rejected. Ambiguous or unknown values fail with a list of valid
 * choices so the caller can correct the dispatch instead of silently inheriting the parent.
 *
 * `null`/blank returns [Inherit] — the run uses the parent assistant's chat model.
 */
internal object SubAgentModelResolver {

    sealed class Result {
        data object Inherit : Result()
        data class Resolved(val modelId: String) : Result()
        data class Failed(val message: String) : Result()
    }

    fun resolve(input: String?, providers: List<ProviderSetting>): Result {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) return Result.Inherit

        val chatModels = providers
            .filter { it.enabled }
            .flatMap { provider ->
                provider.models
                    .filter { it.type == ModelType.CHAT }
                    .map { it to provider }
            }

        // 1) Exact provider model id (case-insensitive).
        chatModels.firstOrNull { (model, _) -> model.modelId.equals(raw, ignoreCase = true) }?.let {
            return Result.Resolved(it.first.modelId)
        }

        // 2) Display name (case-insensitive exact).
        val byName = chatModels.filter { (model, _) -> model.displayName.equals(raw, ignoreCase = true) }
        when (byName.size) {
            0 -> return Result.Failed(unknownModelMessage(raw, chatModels.map { it.first }))
            1 -> return Result.Resolved(byName[0].first.modelId)
            else -> {
                val candidates = byName.joinToString("\n") { (model, provider) ->
                    val providerName = providerNameOf(provider)
                    candidateLine(model, providerName)
                }
                return Result.Failed(
                    "model \"$raw\" matches multiple enabled providers. Pick one of:\n$candidates",
                )
            }
        }
    }

    private fun candidateLine(model: Model, providerName: String): String =
        "${model.displayName} ($providerName) -> ${model.modelId}"

    private fun unknownModelMessage(raw: String, available: List<Model>): String =
        if (available.isEmpty()) {
            "model \"$raw\" did not match any available model, and no CHAT models are configured on an enabled provider."
        } else {
            "model \"$raw\" did not match any CHAT model on an enabled provider. Available models:\n" +
                available.joinToString("\n") { "${it.displayName} -> ${it.modelId}" }
        }

    private fun providerNameOf(provider: ProviderSetting): String = when (provider) {
        is ProviderSetting.OpenAI -> "OpenAI"
        is ProviderSetting.Google -> "Google"
        is ProviderSetting.Claude -> "Claude"
        is ProviderSetting.AICore -> "AICore"
        is ProviderSetting.LiteRtLocal -> "Local LiteRT"
        is ProviderSetting.TaskOcrLocal -> "Local OCR"
        is ProviderSetting.Codex -> "Codex"
        is ProviderSetting.Grok -> "Grok"
        is ProviderSetting.StableDiffusion -> "Stable Diffusion"
    }
}
