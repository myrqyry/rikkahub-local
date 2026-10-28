package me.rerere.rikkahub.data.modelregistry

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.*
import org.junit.Test

class ModelsDevModelMetadataTest {
    private val fixture = """
      {
        "openai": {
          "id":"openai","name":"OpenAI","npm":"@ai-sdk/openai",
          "models":{
            "gpt-5.5":{"id":"gpt-5.5","name":"GPT-5.5",
              "reasoning":true,"tool_call":true,"structured_output":true,
              "temperature":false,
              "modalities":{"input":["text","image","pdf"],"output":["text"]},
              "limit":{"context":1050000,"output":128000},
              "cost":{"input":5.0,"output":30.0},
              "reasoning_options":[{"type":"effort","values":["low","medium","high"]}]
            },
            "gpt-basic":{"id":"gpt-basic","name":"GPT Basic",
              "reasoning":false,"tool_call":false,"temperature":true,
              "modalities":{"input":["text"],"output":["text"]},
              "limit":{"context":8000,"output":2000},
              "cost":{"input":0.5,"output":1.0}
            }
          }
        },
        "openrouter":{
          "id":"openrouter","name":"OpenRouter",
          "api":"https://openrouter.ai/api/v1","npm":"@openrouter/ai-sdk-provider",
          "models":{"openai/gpt-5.5":{"id":"openai/gpt-5.5","name":"GPT-5.5 Router",
            "reasoning":true,"tool_call":false,"temperature":true,
            "modalities":{"input":["text"],"output":["text"]},
            "limit":{"context":9000,"output":1500},
            "cost":{"input":1,"output":4}}}
        },
        "custom":{
          "id":"custom","name":"Custom",
          "api":"https://custom.example/v1","npm":"@ai-sdk/openai-compatible",
          "models":{"custom-model":{"id":"custom-model","name":"Custom Model",
            "reasoning":false,"tool_call":true,
            "modalities":{"input":["text"],"output":["text"]},
            "limit":{"context":25000,"output":6000}}}
        }
      }
    """.trimIndent()

    @Test fun matchesExactProviderAndExactModelIdWithoutCrossProviderLeakage() {
        val catalog = ModelsDevModelCatalog.parse(fixture)
        val openai = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1")
        val openrouter = ProviderSetting.OpenAI(name = "OpenRouter", baseUrl = "https://openrouter.ai/api/v1/")
        val custom = ProviderSetting.OpenAI(name = "Not Custom", baseUrl = "https://custom.example/v1")
        assertEquals("openai", catalog.providerId(openai))
        assertEquals("openrouter", catalog.providerId(openrouter))
        assertEquals("custom", catalog.providerId(custom))
        assertNull(catalog.find(openai, "openai/gpt-5.5"))
        assertNull(catalog.find(openrouter, "gpt-5.5"))
        assertNull(catalog.find(ProviderSetting.OpenAI(name = "Unrelated", baseUrl = "https://somewhere.example/v1"), "gpt-5.5"))
        assertEquals("custom-model", catalog.find(custom, "custom-model")?.modelId)
    }

    @Test fun newModelGetsModesAbilitiesLimitsPricesAndSupportedSettings() {
        val catalog = ModelsDevModelCatalog.parse(fixture)
        val metadata = catalog.find(ProviderSetting.OpenAI(), "gpt-5.5")!!
        val result = metadata.applyToNewModel(Model(modelId = "gpt-5.5"))
        assertEquals("GPT-5.5", result.displayName)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), result.inputModalities)
        assertEquals(listOf(Modality.TEXT), result.outputModalities)
        assertTrue(ModelAbility.TOOL in result.abilities)
        assertTrue(ModelAbility.REASONING in result.abilities)
        assertEquals(1_050_000, result.contextLength)
        assertEquals(128_000, result.maxOutputTokens)
        assertEquals(false, result.supportsTemperature)
        assertEquals(true, result.supportsStructuredOutput)
        assertEquals(listOf("low", "medium", "high"), result.reasoningEfforts)
        assertEquals(0.000005, result.pricePromptPerToken!!, 0.00000000001)
        assertEquals(0.00003, result.priceCompletionPerToken!!, 0.00000000001)
        assertEquals("models.dev:openai/gpt-5.5", result.metadataSource)
        assertTrue("tools" in result.supportedParameters)
        assertFalse("temperature" in result.supportedParameters)
    }

    @Test fun explicitFalseCapabilitiesClearStaticOrDefaultGuessesOnNewModel() {
        val catalog = ModelsDevModelCatalog.parse(fixture)
        val info = catalog.find(ProviderSetting.OpenAI(), "gpt-basic")!!
        val model = Model(
            modelId = "gpt-basic", displayName = "Existing Model",
            abilities = listOf(ModelAbility.REASONING, ModelAbility.TOOL),
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
        )
        val result = info.applyToNewModel(model)
        assertTrue(result.abilities.isEmpty())
        assertEquals(listOf(Modality.TEXT), result.inputModalities)
        assertEquals(true, result.supportsTemperature)
    }

    @Test fun providerReportedCapabilitiesAndPricesWinWhileMissingLimitsCanBeFilled() {
        val catalog = ModelsDevModelCatalog.parse(fixture)
        val info = catalog.find(ProviderSetting.OpenAI(), "gpt-5.5")!!
        val reported = Model(
            modelId = "gpt-5.5", displayName = "Provider display name",
            inputModalities = listOf(Modality.TEXT),
            abilities = listOf(ModelAbility.TOOL),
            supportedParameters = listOf("provider-specific"),
            pricePromptPerToken = 0.00002,
            contextLength = 12345,
        )
        val result = info.enrichProviderModel(reported)
        assertEquals(reported.displayName, result.displayName)
        assertEquals(reported.inputModalities, result.inputModalities)
        assertEquals(reported.abilities, result.abilities)
        assertEquals(reported.supportedParameters, result.supportedParameters)
        assertEquals(reported.contextLength, result.contextLength)
        assertEquals(reported.pricePromptPerToken, result.pricePromptPerToken)
        assertEquals(128_000, result.maxOutputTokens)
    }

    @Test fun providerReportedTemperatureParameterOverridesCatalogDisagreement() {
        val info = ModelsDevModelCatalog.parse(fixture).find(ProviderSetting.OpenAI(), "gpt-5.5")!!
        val providerModel = Model(
            modelId = "gpt-5.5", metadataSource = "provider:openrouter",
            supportedParameters = listOf("temperature"),
        )
        val enriched = info.enrichProviderModel(providerModel)
        assertEquals(true, enriched.supportsTemperature)
        assertEquals(providerModel.supportedParameters, enriched.supportedParameters)
    }

    @Test fun providerReportedNegativeCapabilitiesAlsoWinWithoutParameterList() {
        val catalog = ModelsDevModelCatalog.parse(fixture)
        val info = catalog.find(ProviderSetting.OpenAI(), "gpt-5.5")!!
        val native = Model(
            modelId = "gpt-5.5", displayName = "Native name",
            metadataSource = "provider:openrouter",
            inputModalities = listOf(Modality.TEXT),
            abilities = emptyList(),
            supportedParameters = emptyList(),
        )
        val enriched = info.enrichProviderModel(native)
        assertEquals(native.abilities, enriched.abilities)
        assertEquals(native.inputModalities, enriched.inputModalities)
        assertEquals(native.displayName, enriched.displayName)
        assertEquals(native.metadataSource, enriched.metadataSource)
        assertEquals(128_000, enriched.maxOutputTokens)
    }

    @Test fun modelIdChangesDoNotGuessCanonicalAliasesOrOtherProviders() {
        val catalog = ModelsDevModelCatalog.parse(fixture)
        assertNull(catalog.find(ProviderSetting.OpenAI(), "GPT-5.5"))
        assertNull(catalog.find(ProviderSetting.OpenAI(baseUrl = "https://custom.example/v1/evil"), "custom-model"))
        assertNull(catalog.find(ProviderSetting.Google(name = "Gemini"), "gpt-5.5"))
    }

    @Test fun anOpenAiAdapterNeverAutoImportsNativeProtocolMetadata() {
        val raw = """{"native":{"id":"native","name":"Native","npm":"@ai-sdk/anthropic",
            "api":"https://native.example/v1","models":{"model":{"id":"model","name":"Model"}}}}"""
        val catalog = ModelsDevModelCatalog.parse(raw)
        val openAiAdapter = ProviderSetting.OpenAI(baseUrl = "https://native.example/v1")
        assertNull(catalog.find(openAiAdapter, "model"))
    }

    @Test fun localhostServicesAreNotMatchedFromPublicCatalogByPortAlone() {
        val raw = """{"local":{"id":"local","name":"Local","api":"http://127.0.0.1:1337/v1",
            "models":{"some":{"id":"some","name":"Some"}}}}"""
        val catalog = ModelsDevModelCatalog.parse(raw)
        assertNull(catalog.providerId(ProviderSetting.OpenAI(baseUrl = "http://127.0.0.1:1337/v1")))
    }

    @Test fun providerScopedParseKeepsOnlyTheSelectedEndpointModels() {
        val selected = ProviderSetting.OpenAI(baseUrl = "https://openrouter.ai/api/v1")
        val catalog = ModelsDevModelCatalog.parseForProvider(fixture, selected)
        assertEquals("openrouter", catalog.providerId(selected))
        assertNotNull(catalog.find(selected, "openai/gpt-5.5"))
        assertNull(catalog.find(ProviderSetting.OpenAI(), "gpt-5.5"))
    }

    @Test fun missingModalitiesAreNotGuessedToBeSupportedChatInCatalogPicker() {
        val raw = """{"unknown-host":{"id":"unknown-host","name":"Unknown",
          "npm":"@ai-sdk/openai-compatible","api":"https://unknown.example/v1",
          "models":{"unknown":{"id":"unknown","name":"Unknown"}}}}"""
        val provider = ProviderSetting.OpenAI(baseUrl = "https://unknown.example/v1")
        val catalog = ModelsDevModelCatalog.parse(raw)
        assertTrue(catalog.suggestedModels(provider).isEmpty())
    }

    @Test fun imageOutputIsTypedImageAndNonChatSpecializedModelsAreNotSuggested() {
        val raw = """{"p":{"id":"p","name":"P","npm":"@ai-sdk/openai-compatible","api":"https://p.example/v1","models":{
          "draw":{"id":"draw","name":"Draw","modalities":{"input":["text"],"output":["image"]}},
          "voice":{"id":"voice","name":"Voice","type":"realtime","modalities":{"input":["audio"],"output":["audio"]}}
        }}}"""
        val catalog = ModelsDevModelCatalog.parse(raw)
        val provider = ProviderSetting.OpenAI(baseUrl = "https://p.example/v1")
        assertEquals(ModelType.IMAGE, catalog.find(provider, "draw")!!.applyToNewModel(Model(modelId = "draw")).type)
        assertEquals(listOf("draw"), catalog.suggestedModels(provider).map { it.modelId })
    }
}
