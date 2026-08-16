package me.rerere.rikkahub.data.ai.tools.image

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.GeneratedImagePayload
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.ToolInvocationContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.media.ImageMediaStore
import me.rerere.rikkahub.data.media.MediaArtifactRef
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.modelregistry.ModelAssignments
import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelProviderDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelRegistry
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelRoleResolver
import me.rerere.rikkahub.data.modelregistry.ModelSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

// Uuid-parseable descriptor ids: the production flow does
// settings.findModelById(Uuid.parse(descriptor.model.id)) which throws on non-UUID strings.
private val GEN_DESCRIPTOR_ID = "c10a4d3e-0001-4a4d-8001-000000000001"
private val CLOUD_PROVIDER_ID = Uuid.parse("c10a4d3e-0001-4a4d-8001-000000000003")

class ImageToolsTest {

    private fun catalog(): ImageTools = ImageTools(
        settingsStore = FakeSettingsStore(),
        modelRoleResolver = stubResolver(),
        imageToolBackend = StubBackend(),
        imageMediaStore = stubStore(),
        mediaInputResolver = stubResolverInput(),
        imageTextExtractor = stubExtractor(),
    )

    @Test
    fun `all four tools registered regardless of chat model`() {
        val names = catalog().tools().map { it.name }
        assertTrue(names.containsAll(ImageToolCatalog.TOOL_NAMES))
        assertEquals(4, names.size)
    }

    @Test
    fun `generate and edit are statically approval-gated`() {
        val args = buildJsonObject { put("prompt", "a cat") }
        val tools = catalog().tools().associateBy { it.name }
        assertTrue(tools.getValue("generate_image").needsApproval(args))
        assertTrue(tools.getValue("edit_image").needsApproval(args))
        assertFalse(tools.getValue("analyze_image").needsApproval(args))
        assertFalse(tools.getValue("extract_text_from_image").needsApproval(args))
    }

    @Test
    fun `tool output path passes into another tool`() = runBlocking {
        val result = catalog().tools().first { it.name == "generate_image" }.execute(
            buildJsonObject {
                put("prompt", "a cat")
                put("aspect_ratio", "1:1")
                put("count", 1)
            },
        )
        val text = result.filterIsInstance<UIMessagePart.Text>().single().text
        assertTrue(text.contains("img_1"))
        assertTrue(text.contains("/tmp/1.png"))
    }

    // ---- fakes (same scaffolding as ImageTextExtractorTest: real ModelRoleResolver over a
    // fake ModelRegistry + a real Settings whose provider models match the descriptor id) ----

    private class FakeSettingsStore : SettingsProvider {
        private val stub = SettingsStub()
        override fun current(): Settings = stub.settings
    }

    private fun stubResolver(): ModelRoleResolver {
        val descriptor = ModelDescriptor(
            id = GEN_DESCRIPTOR_ID,
            displayName = "fake",
            source = ModelSource.Cloud(providerId = CLOUD_PROVIDER_ID.toString(), remoteModelId = "gemini"),
            capabilities = setOf(ModelCapability.IMAGE_GENERATION),
            lifecycle = ModelLifecycle.AVAILABLE,
        )
        return ModelRoleResolver(FakeModelRegistry(descriptor, GEN_DESCRIPTOR_ID))
    }

    private class StubBackend : ImageToolBackend {
        override suspend fun generateImage(
            providerSetting: ProviderSetting,
            params: ImageGenerationParams,
        ): Flow<ImageGenerationItem> =
            flowOf(ImageGenerationItem(payload = GeneratedImagePayload.Base64("base64", "image/png"), partial = false))

        override suspend fun editImage(
            providerSetting: ProviderSetting,
            params: ImageEditParams,
        ): Flow<ImageGenerationItem> =
            flowOf(ImageGenerationItem(payload = GeneratedImagePayload.Base64("base64", "image/png"), partial = false))

        override suspend fun generateText(
            providerSetting: ProviderSetting,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk = MessageChunk(
            id = "fake",
            model = "fake",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text("canned reply")),
                    ),
                    finishReason = null,
                ),
            ),
        )
    }

    private fun stubStore(): ImageMediaStore = object : ImageMediaStore {
        override suspend fun saveGenerated(
            item: ImageGenerationItem,
            prompt: String,
            model: ModelDescriptor,
            operation: ImageOperation,
            sourceArtifacts: List<MediaArtifactRef>,
        ): StoredImageArtifact = StoredImageArtifact(
            artifactId = "img_1",
            path = "/tmp/1.png",
            uri = "file:///tmp/1.png",
            galleryId = 1,
            mimeType = "image/png",
            width = 64,
            height = 64,
        )
    }

    private fun stubResolverInput(): MediaInputResolver = object : MediaInputResolver {
        override suspend fun resolveImage(
            reference: String,
            executionContext: ToolInvocationContext,
        ): ResolvedMedia = ResolvedMedia(
            stablePath = "/tmp/1.png",
            originalReference = "img_1",
            mimeType = "image/png",
            sizeBytes = 1024,
            temporary = false,
        )
    }

    private fun stubExtractor(): ImageTextExtractor = ImageTextExtractor(StubBackend(), stubResolver())

    private class FakeModelRegistry(
        descriptor: ModelDescriptor,
        defaultId: String,
    ) : ModelRegistry {
        override val models = MutableStateFlow(listOf(descriptor))
        override val providers = MutableStateFlow<List<ModelProviderDescriptor>>(emptyList())
        override val assignments = MutableStateFlow(
            ModelAssignments(defaults = mapOf(ModelRole.IMAGE_GENERATION to defaultId)),
        )
        override suspend fun refreshProvider(providerId: String) = Unit
        override suspend fun setCapabilityEnabled(modelId: String, capability: ModelCapability, enabled: Boolean) = Unit
        override suspend fun assign(role: ModelRole, modelId: String?) = Unit
        override suspend fun install(modelId: String) = Unit
        override suspend fun remove(modelId: String) = Unit
        override suspend fun rename(modelId: String, newDisplayName: String) = Unit
    }

    private class SettingsStub {
        val settings: Settings = Settings(
            providers = listOf(
                ProviderSetting.Google(
                    id = CLOUD_PROVIDER_ID,
                    enabled = true,
                    models = listOf(
                        Model(id = Uuid.parse(GEN_DESCRIPTOR_ID), modelId = "gemini", displayName = "Gemini"),
                    ),
                ),
            ),
            assistants = listOf(Assistant()),
        )
    }
}
