package me.rerere.rikkahub.data.modelregistry

import kotlin.uuid.Uuid
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.DEFAULT_PROVIDERS

/** A models.dev listing is not proof that a provider API is supported by our adapters. */
sealed interface ProviderDirectoryAction {
    data class OpenExisting(val provider: ProviderSetting) : ProviderDirectoryAction
    data class CreateDraft(val provider: ProviderSetting) : ProviderDirectoryAction
    data object Unsupported : ProviderDirectoryAction
}

/** Rendering order and safe selection for the primary provider API directory. */
object ModelsDevProviderPresentation {
    // Curated presentation priority, not a second catalog or a list of supported APIs.
    val pinnedIds = listOf(
        "openai", "anthropic", "google", "openrouter", "deepseek", "xai",
        "groq", "mistral", "cerebras",
    )

    // Verified OpenAI Chat Completions endpoints for popular providers whose
    // models.dev "npm" field names a native SDK instead of listing an API URL.
    // This enables basic chat configuration, NOT parity with their native SDKs.
    // https://console.groq.com/docs/openai
    // https://docs.mistral.ai/resources/migration-guides
    // https://inference-docs.cerebras.ai/resources/openai
    private val verifiedChatEndpoints = mapOf(
        "groq" to "https://api.groq.com/openai/v1",
        "mistral" to "https://api.mistral.ai/v1",
        "cerebras" to "https://api.cerebras.ai/v1",
    )

    fun pinned(entries: List<ModelsDevProviderEntry>, query: String = ""): List<ModelsDevProviderEntry> {
        val filtered = matching(entries, query).associateBy { it.id }
        return pinnedIds.mapNotNull(filtered::get)
    }

    fun other(entries: List<ModelsDevProviderEntry>, query: String = ""): List<ModelsDevProviderEntry> =
        matching(entries, query)
            .filterNot { it.id in pinnedIds }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun resolve(
        entry: ModelsDevProviderEntry,
        configuredProviders: List<ProviderSetting>,
        defaults: List<ProviderSetting> = DEFAULT_PROVIDERS,
    ): ProviderDirectoryAction {
        // The original app has real adapters and predefined endpoint settings.
        // Resolve those existing records FIRST: no key, model, ID or custom setting changes.
        val template = defaults.firstOrNull { provider ->
            when (entry.id) {
                "google" -> provider is ProviderSetting.Google &&
                    provider.baseUrl.startsWith("https://generativelanguage.googleapis.com/")
                "anthropic" -> provider is ProviderSetting.Claude &&
                    provider.baseUrl.startsWith("https://api.anthropic.com/")
                "openai" -> provider is ProviderSetting.OpenAI &&
                    provider.baseUrl.trimEnd('/') == "https://api.openai.com/v1"
                else -> (provider is ProviderSetting.OpenAI ||
                    provider is ProviderSetting.Claude ||
                    provider is ProviderSetting.Google) &&
                    provider.name.equals(entry.name, ignoreCase = true)
            }
        }

        val existing = configuredProviders.firstOrNull { provider ->
            template != null && provider.id == template.id &&
                provider::class == template::class
        } ?: configuredProviders.firstOrNull { provider ->
            when (entry.id) {
                "google" -> provider is ProviderSetting.Google &&
                    urlsEqual(provider.baseUrl, "https://generativelanguage.googleapis.com/v1beta")
                "anthropic" -> provider is ProviderSetting.Claude &&
                    urlsEqual(provider.baseUrl, "https://api.anthropic.com/v1")
                "openai" -> provider is ProviderSetting.OpenAI &&
                    urlsEqual(provider.baseUrl, "https://api.openai.com/v1")
                else -> provider is ProviderSetting.OpenAI && (
                    urlsEqual(provider.baseUrl, entry.baseUrl) ||
                    (template is ProviderSetting.OpenAI &&
                        urlsEqual(provider.baseUrl, template.baseUrl)) ||
                    urlsEqual(provider.baseUrl, verifiedChatEndpoints[entry.id])
                    )
            }
        }
        if (existing != null) return ProviderDirectoryAction.OpenExisting(existing)

        // A deleted built-in is never resurrected silently; only the user's
        // explicit selection opens an unconfigured, disabled clone of its adapter.
        if (template != null) {
            val draft = template.copyProvider(
                id = Uuid.random(),
                enabled = false,
                models = emptyList(),
                builtIn = false,
            )
            return ProviderDirectoryAction.CreateDraft(draft)
        }

        when (entry.id) {
            "google" -> return ProviderDirectoryAction.CreateDraft(
                ProviderSetting.Google(
                    name = "Gemini", apiKey = "", enabled = false, builtIn = false,
                )
            )
            "anthropic" -> return ProviderDirectoryAction.CreateDraft(
                ProviderSetting.Claude(
                    name = "Anthropic", apiKey = "", enabled = false, builtIn = false,
                )
            )
            "openai" -> return ProviderDirectoryAction.CreateDraft(
                ProviderSetting.OpenAI(
                    name = "OpenAI", apiKey = "", enabled = false, builtIn = false,
                )
            )
        }
        verifiedChatEndpoints[entry.id]?.let { endpoint ->
            return ProviderDirectoryAction.CreateDraft(
                ProviderSetting.OpenAI(
                    name = entry.name,
                    baseUrl = endpoint,
                    apiKey = "",
                    enabled = false,
                    builtIn = false,
                )
            )
        }
        if (entry.canAddAsOpenAiCompatible) {
            return ProviderDirectoryAction.CreateDraft(entry.toProviderDraft())
        }
        return ProviderDirectoryAction.Unsupported
    }

    private fun matching(entries: List<ModelsDevProviderEntry>, query: String): List<ModelsDevProviderEntry> {
        val search = query.trim()
        return entries.filter {
            search.isEmpty() || it.id.contains(search, ignoreCase = true) ||
                it.name.contains(search, ignoreCase = true)
        }
    }

    private fun urlsEqual(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() &&
            a.trim().trimEnd('/').equals(b.trim().trimEnd('/'), ignoreCase = true)
}
