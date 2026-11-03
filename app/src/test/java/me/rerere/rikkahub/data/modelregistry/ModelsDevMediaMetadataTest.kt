package me.rerere.rikkahub.data.modelregistry

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsDevMediaMetadataTest {
    private val fixture = """
        {"example":{"id":"example","name":"Example","npm":"@ai-sdk/openai-compatible",
          "api":"https://example.test/v1","models":{
            "multimodal":{"id":"multimodal","name":"Multimodal",
              "modalities":{"input":["text","image","audio","video","pdf"],
                            "output":["text","audio"]}},
            "audio-only":{"id":"audio-only","name":"Audio only",
              "modalities":{"input":["audio"],"output":["audio"]}}
        }}}
    """.trimIndent()

    private val provider = ProviderSetting.OpenAI(baseUrl = "https://example.test/v1")

    @Test fun catalogRetainsAllAdvertisedInputAndOutputMediaWithoutClaimingRuntimeTransport() {
        val metadata = ModelsDevModelCatalog.parse(fixture).find(provider, "multimodal")!!
        val imported = metadata.applyToNewModel(Model(modelId = "multimodal"))
        assertEquals(listOf("text", "image", "audio", "video", "pdf"), imported.advertisedInputModalities)
        assertEquals(listOf("text", "audio"), imported.advertisedOutputModalities)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), imported.inputModalities)
        assertEquals(listOf(Modality.TEXT), imported.outputModalities)
    }

    @Test fun existingRichProviderModalitiesWinAndCatalogMediaAreStillInformational() {
        val metadata = ModelsDevModelCatalog.parse(fixture).find(provider, "multimodal")!!
        val providerModel = Model(
            modelId = "multimodal",
            metadataSource = "provider:openrouter",
            inputModalities = listOf(Modality.TEXT),
            outputModalities = listOf(Modality.TEXT),
            supportedParameters = listOf("tools"),
        )
        val imported = metadata.enrichProviderModel(providerModel)
        assertEquals(providerModel.inputModalities, imported.inputModalities)
        assertEquals(providerModel.outputModalities, imported.outputModalities)
        assertEquals(listOf("text", "image", "audio", "video", "pdf"), imported.advertisedInputModalities)
    }

    @Test fun catalogAudioOnlyDoesNotGetOfferedAsWorkingChatOrImageModel() {
        val catalog = ModelsDevModelCatalog.parse(fixture)
        assertTrue(catalog.suggestedModels(provider).none { it.modelId == "audio-only" })
    }

    @Test fun oldSettingsRemainSerializableWithoutAdvertisedMediaMetadata() {
        val original = Model(modelId = "user-created")
        val json = kotlinx.serialization.json.Json.encodeToString(Model.serializer(), original)
        val result = kotlinx.serialization.json.Json.decodeFromString(Model.serializer(), json)
        assertEquals(original, result)
        assertTrue(result.advertisedInputModalities.isEmpty())
        assertTrue(result.advertisedOutputModalities.isEmpty())
    }
}
