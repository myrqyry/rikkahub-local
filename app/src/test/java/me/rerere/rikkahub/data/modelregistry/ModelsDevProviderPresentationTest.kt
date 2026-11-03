package me.rerere.rikkahub.data.modelregistry

import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.DEFAULT_PROVIDERS
import org.junit.Assert.*
import org.junit.Test

class ModelsDevProviderPresentationTest {
    private fun entry(
        id: String,
        name: String = id,
        compatible: Boolean = false,
        url: String? = null,
        count: Int = 10,
    ) = ModelsDevProviderEntry(id, name, url, count, compatible)

    @Test fun popularProvidersComeFirstAndAllOthersStayAlphabeticalWithoutDuplicates() {
        val entries = listOf(
            entry("zeta", "Zeta"),
            entry("google", "Google"),
            entry("deepseek", "DeepSeek"),
            entry("groq", "Groq"),
            entry("openai", "OpenAI"),
            entry("alpha", "Alpha"),
            entry("openrouter", "OpenRouter"),
            entry("anthropic", "Anthropic"),
            entry("xai", "xAI"),
        )
        assertEquals(
            listOf("openai", "anthropic", "google", "openrouter", "deepseek", "xai", "groq"),
            ModelsDevProviderPresentation.pinned(entries).map { it.id },
        )
        assertEquals(
            listOf("alpha", "zeta"),
            ModelsDevProviderPresentation.other(entries).map { it.id },
        )
        assertEquals(entries.size, (
            ModelsDevProviderPresentation.pinned(entries) +
                ModelsDevProviderPresentation.other(entries)
            ).map { it.id }.distinct().size)
    }

    @Test fun searchFiltersPinnedAndOtherWithoutChangingTheirOrder() {
        val entries = listOf(
            entry("google", "Google"), entry("openai", "OpenAI"),
            entry("groq", "Groq"), entry("novelhost", "Novel Host"),
        )
        assertEquals(listOf("google", "groq"),
            ModelsDevProviderPresentation.pinned(entries, "g").map { it.id })
        assertTrue(ModelsDevProviderPresentation.other(entries, "g").isEmpty())
        assertEquals(listOf("novelhost"),
            ModelsDevProviderPresentation.other(entries, "NOVEL").map { it.id })
    }

    @Test fun nativeGoogleOpensExistingGeminiInsteadOfDuplicatingIt() {
        val google = DEFAULT_PROVIDERS.filterIsInstance<ProviderSetting.Google>().single()
        val action = ModelsDevProviderPresentation.resolve(
            entry("google", "Google"), DEFAULT_PROVIDERS
        )
        assertEquals(ProviderDirectoryAction.OpenExisting(google), action)
    }

    @Test fun openRouterDeepSeekAndXaiReuseBuiltInProvidersAndKeepTheirIds() {
        for ((id, name) in listOf(
            "openrouter" to "OpenRouter",
            "deepseek" to "DeepSeek",
            "xai" to "xAI",
        )) {
            val expected = DEFAULT_PROVIDERS.first { it.name == name }
            val action = ModelsDevProviderPresentation.resolve(entry(id, name), DEFAULT_PROVIDERS)
            assertEquals("Missing existing provider for $id",
                ProviderDirectoryAction.OpenExisting(expected), action)
        }
    }

    @Test fun nativeAnthropicCreatesTypedClaudeDraftWithNoSecretsOrConnectionEnabled() {
        val action = ModelsDevProviderPresentation.resolve(
            entry("anthropic", "Anthropic"), DEFAULT_PROVIDERS
        )
        assertTrue(action is ProviderDirectoryAction.CreateDraft)
        val draft = (action as ProviderDirectoryAction.CreateDraft).provider
        assertTrue(draft is ProviderSetting.Claude)
        assertEquals("https://api.anthropic.com/v1", (draft as ProviderSetting.Claude).baseUrl)
        assertTrue(draft.apiKey.isBlank())
        assertFalse(draft.enabled)
        assertFalse(draft.builtIn)
    }

    @Test fun nativeGoogleAndOpenAiRecreateSupportedDraftsIfTheirDefaultWasDeleted() {
        for (id in listOf("google", "openai")) {
            val action = ModelsDevProviderPresentation.resolve(entry(id, id), emptyList())
            assertTrue(action is ProviderDirectoryAction.CreateDraft)
            val draft = (action as ProviderDirectoryAction.CreateDraft).provider
            assertFalse(draft.enabled)
            assertFalse(draft.builtIn)
            assertTrue(draft.models.isEmpty())
            when (id) {
                "google" -> {
                    assertTrue(draft is ProviderSetting.Google)
                    assertTrue((draft as ProviderSetting.Google).apiKey.isEmpty())
                }
                "openai" -> {
                    assertTrue(draft is ProviderSetting.OpenAI)
                    assertTrue((draft as ProviderSetting.OpenAI).apiKey.isEmpty())
                }
            }
        }
    }

    @Test fun compatibleUnlistedProviderCreatesDisabledBlankKeyDraft() {
        val e = entry("safehost", "Safe Host", compatible = true,
            url = "https://safe.example/v1")
        val action = ModelsDevProviderPresentation.resolve(e, DEFAULT_PROVIDERS)
        assertTrue(action is ProviderDirectoryAction.CreateDraft)
        val draft = (action as ProviderDirectoryAction.CreateDraft).provider as ProviderSetting.OpenAI
        assertEquals("https://safe.example/v1", draft.baseUrl)
        assertTrue(draft.apiKey.isEmpty())
        assertFalse(draft.enabled)
    }

    @Test fun officiallyVerifiedOpenAiCompatiblePopularApisOfferSafeDrafts() {
        val verified = mapOf(
            "groq" to "https://api.groq.com/openai/v1",
            "mistral" to "https://api.mistral.ai/v1",
            "cerebras" to "https://api.cerebras.ai/v1",
        )
        for ((id, url) in verified) {
            val action = ModelsDevProviderPresentation.resolve(
                entry(id, id.replaceFirstChar { it.uppercase() }), DEFAULT_PROVIDERS
            )
            assertTrue("$id should have an explicit API adapter", action is ProviderDirectoryAction.CreateDraft)
            val draft = (action as ProviderDirectoryAction.CreateDraft).provider as ProviderSetting.OpenAI
            assertEquals(url, draft.baseUrl)
            assertEquals("", draft.apiKey)
            assertFalse(draft.enabled)
            assertFalse(draft.builtIn)
        }
    }

    @Test fun existingVerifiedProviderEndpointIsReopenedWithoutOverwritingCredentials() {
        val existing = ProviderSetting.OpenAI(
            name = "My Groq", baseUrl = "https://api.groq.com/openai/v1",
            apiKey = "preserve-me", enabled = true,
        )
        val action = ModelsDevProviderPresentation.resolve(
            entry("groq", "Groq"), listOf(existing)
        )
        assertEquals(ProviderDirectoryAction.OpenExisting(existing), action)
    }

    @Test fun catalogOnlyUnknownNativeProviderIsDiscoverableButNotFalselyAddable() {
        val action = ModelsDevProviderPresentation.resolve(
            entry("unknown-native", "Unknown Native"), DEFAULT_PROVIDERS
        )
        assertEquals(ProviderDirectoryAction.Unsupported, action)
    }

    @Test fun existingCustomEndpointOpensItsOwnConfiguration() {
        val existing = ProviderSetting.OpenAI(
            name = "My Private Host",
            baseUrl = "https://safe.example/v1/",
            apiKey = "keep-secret",
            enabled = true,
        )
        val entry = entry("safehost", "Safe Host", compatible = true,
            url = "https://safe.example/v1")
        val action = ModelsDevProviderPresentation.resolve(entry, listOf(existing))
        assertEquals(ProviderDirectoryAction.OpenExisting(existing), action)
        assertEquals("keep-secret", (action as ProviderDirectoryAction.OpenExisting)
            .provider.let { it as ProviderSetting.OpenAI }.apiKey)
    }

    @Test fun unconfiguredDefaultProviderIsNotMutatedWhenGeneratingANewDraft() {
        val default = DEFAULT_PROVIDERS.first { it.name == "OpenRouter" }
        val action = ModelsDevProviderPresentation.resolve(
            entry("openrouter", "OpenRouter"), emptyList())
        val draft = (action as ProviderDirectoryAction.CreateDraft).provider
        assertEquals(default.name, draft.name)
        assertNotEquals(default.id, draft.id)
        assertFalse(draft.builtIn)
        assertFalse(draft.enabled)
        assertTrue(draft.models.isEmpty())
    }
}
