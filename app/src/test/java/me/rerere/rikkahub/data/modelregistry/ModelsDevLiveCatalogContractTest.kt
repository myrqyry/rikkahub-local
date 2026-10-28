package me.rerere.rikkahub.data.modelregistry

import java.io.File
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.composerReasoningLevels
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in acceptance check using a real downloaded models.dev/api.json snapshot.
 * Public fixture is stored outside Git; no user credentials or provider calls are needed.
 */
class ModelsDevLiveCatalogContractTest {
    @Test fun currentPublicCatalogCanPopulateRealProviderModelMetadata() {
        val source = System.getenv("RIKKAHUB_MODELS_DEV_LIVE_JSON")
        assumeTrue("Set RIKKAHUB_MODELS_DEV_LIVE_JSON to run against live public metadata",
            !source.isNullOrBlank() && File(source).isFile)
        val json = File(source!!).readText()
        val openAi = ProviderSetting.OpenAI()
        val openAiCatalog = ModelsDevModelCatalog.parseForProvider(json, openAi)
        assertEquals("openai", openAiCatalog.providerId(openAi))
        val gpt4o = openAiCatalog.find(openAi, "gpt-4o")
            ?: throw AssertionError("Public OpenAI gpt-4o entry is missing")
        val imported = gpt4o.applyToNewModel(me.rerere.ai.provider.Model(modelId = "gpt-4o"))
        assertTrue(imported.contextLength?.let { it > 0 } == true)
        assertTrue(ModelAbility.TOOL in imported.abilities)
        assertNotNull(imported.pricePromptPerToken)

        val gpt55 = openAiCatalog.find(openAi, "gpt-5.5")
            ?: throw AssertionError("Public OpenAI gpt-5.5 entry is missing")
        val configured = gpt55.applyToNewModel(me.rerere.ai.provider.Model(modelId = "gpt-5.5"))
        assertEquals(listOf(ReasoningLevel.OFF, ReasoningLevel.AUTO, ReasoningLevel.LOW,
            ReasoningLevel.MEDIUM, ReasoningLevel.HIGH, ReasoningLevel.XHIGH),
            configured.composerReasoningLevels(openAi))

        val google = ProviderSetting.Google()
        val googleCatalog = ModelsDevModelCatalog.parseForProvider(json, google)
        val gemini = googleCatalog.find(google, "gemini-3.5-flash")
            ?: throw AssertionError("Public Gemini 3.5 Flash entry is missing")
        val geminiModel = gemini.applyToNewModel(me.rerere.ai.provider.Model(modelId = "gemini-3.5-flash"))
        assertEquals(listOf(ReasoningLevel.AUTO, ReasoningLevel.LOW,
            ReasoningLevel.MEDIUM, ReasoningLevel.HIGH), geminiModel.composerReasoningLevels(google))

        val router = ProviderSetting.OpenAI(name = "OpenRouter", baseUrl = "https://openrouter.ai/api/v1")
        val routerCatalog = ModelsDevModelCatalog.parseForProvider(json, router)
        assertEquals("openrouter", routerCatalog.providerId(router))
        assertNotNull(routerCatalog.find(router, "openai/gpt-4o"))
        assertNull(routerCatalog.find(router, "gpt-4o"))
    }
}
