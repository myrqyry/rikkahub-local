package me.rerere.rikkahub.data.modelregistry

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.*
import org.junit.Test

class ModelImportResolutionTest {
    private val provider = ProviderSetting.OpenAI()
    private val catalog = ModelsDevModelCatalog.parse("""
      {"openai":{"id":"openai","name":"OpenAI","npm":"@ai-sdk/openai","models":{
        "new-model":{"id":"new-model","name":"New Model","reasoning":true,"tool_call":true,
          "modalities":{"input":["text","image","pdf","audio"],"output":["text"]},
          "limit":{"context":262144,"output":8192}}
      }}}
    """.trimIndent())

    @Test fun modelsFetchedWithPreviousProviderCredentialsCannotAppearForAnotherConfig() {
        val first = ProviderSetting.OpenAI(apiKey = "key-a")
        val models = ProviderScopedModelList(first, listOf(Model(modelId = "only-a")))
        assertEquals(listOf("only-a"), models.forProvider(first).map { it.modelId })
        assertTrue(models.forProvider(first.copy(apiKey = "key-b")).isEmpty())
        assertTrue(models.forProvider(ProviderSetting.OpenAI(apiKey = "key-a")).isEmpty())
    }

    @Test fun resultFromPreviousProviderCannotUnlockImportForNewProvider() {
        val old = ProviderScopedCatalogLoad(
            providerId = provider.id,
            endpoint = provider.baseUrl,
            load = ModelCatalogLoad.Ready(catalog),
        )
        val switched = ProviderSetting.OpenAI(baseUrl = "https://other-provider.example/v1")
        assertEquals(ModelCatalogLoad.Loading, old.forProvider(switched.id, switched.baseUrl))
        assertEquals(ModelCatalogLoad.Loading, old.forProvider(provider.id, "https://changed-host.example/v1"))
        assertNotNull(old.forProvider(provider.id, provider.baseUrl).resolveImport(Model(modelId = "new-model"), provider))
    }

    @Test fun unresolvedCatalogMustNotCreateAnUnconfiguredSavedModel() {
        val pending = ModelCatalogLoad.Loading
        assertNull(pending.resolveImport(Model(modelId = "new-model"), provider))
    }

    @Test fun readyCatalogImportsProviderScopedMetadataExactlyOnce() {
        val state = ModelCatalogLoad.Ready(catalog)
        val resolved = state.resolveImport(Model(modelId = "new-model"), provider)!!
        assertEquals("New Model", resolved.displayName)
        assertEquals(262144, resolved.contextLength)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), resolved.inputModalities)
        assertEquals(listOf("text", "image", "pdf", "audio"), resolved.advertisedInputModalities)
        assertTrue(ModelAbility.REASONING in resolved.abilities)
        assertTrue(ModelAbility.TOOL in resolved.abilities)
    }

    @Test fun failedCatalogStillAllowsManualAndProviderModelImports() {
        val state = ModelCatalogLoad.Ready(null)
        val resolved = state.resolveImport(Model(modelId = "unknown-model", displayName = "Custom"), provider)
        assertNotNull(resolved)
        assertEquals("Custom", resolved!!.displayName)
        assertNull(resolved.metadataSource)
    }

    @Test fun alreadyImportedModelKeepsProviderReportedMetadata() {
        val rich = Model(
            modelId = "new-model", displayName = "Provider name",
            metadataSource = "provider:openrouter",
            supportedParameters = listOf("tools"),
            inputModalities = listOf(Modality.TEXT),
        )
        val result = ModelCatalogLoad.Ready(catalog).resolveImport(rich, provider)!!
        assertEquals("Provider name", result.displayName)
        assertEquals(rich.inputModalities, result.inputModalities)
        assertEquals(rich.metadataSource, result.metadataSource)
        assertEquals(262144, result.contextLength)
    }

    @Test fun anUnrelatedProviderCannotReuseCatalogCapabilities() {
        val otherProvider = ProviderSetting.OpenAI(baseUrl = "https://another-provider.example/v1")
        val result = ModelCatalogLoad.Ready(catalog)
            .resolveImport(Model(modelId = "new-model"), otherProvider)!!
        assertNull(result.metadataSource)
    }
}
