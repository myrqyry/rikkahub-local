package me.rerere.rikkahub.data.ai.generation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.GeneratedImagePayload
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.image.ImageOperation
import me.rerere.rikkahub.data.ai.tools.image.ImageToolBackend
import me.rerere.rikkahub.data.ai.tools.image.StoredImageArtifact
import me.rerere.rikkahub.data.media.ImageMediaStore
import me.rerere.rikkahub.data.media.MediaArtifactRef
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
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
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

// Uuid-parseable descriptor ids: the production flow does
// settings.findModelById(Uuid.parse(descriptor.model.id)) which throws on non-UUID strings.
private val GEN_DESCRIPTOR_ID = "c10a4d3e-0001-4a4d-8001-000000000001"
private val CLOUD_PROVIDER_ID = Uuid.parse("c10a4d3e-0001-4a4d-8001-000000000003")

class GenerationServiceTest {

    private open class StubBackend : ImageToolBackend {
        var generateCalls = 0
        var editCalls = 0
        override suspend fun generateImage(
            providerSetting: ProviderSetting,
            params: ImageGenerationParams,
        ): Flow<ImageGenerationItem> {
            generateCalls++
            return flowOf(ImageGenerationItem(payload = GeneratedImagePayload.Base64("base64", "image/png"), partial = false))
        }

        override suspend fun editImage(
            providerSetting: ProviderSetting,
            params: ImageEditParams,
        ): Flow<ImageGenerationItem> {
            editCalls++
            return flowOf(ImageGenerationItem(payload = GeneratedImagePayload.Base64("base64", "image/png"), partial = false))
        }

        override suspend fun generateText(
            providerSetting: ProviderSetting,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk = MessageChunk(
            id = "test-double",
            model = "test-double",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = UIMessage(
                        role = me.rerere.ai.core.MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text("canned reply")),
                    ),
                    finishReason = null,
                ),
            ),
        )
    }

    private class StubStore : ImageMediaStore {
        var lastSourceArtifacts: List<MediaArtifactRef>? = null
        override suspend fun saveGenerated(
            item: ImageGenerationItem,
            prompt: String,
            model: ModelDescriptor,
            operation: ImageOperation,
            sourceArtifacts: List<MediaArtifactRef>,
        ): StoredImageArtifact {
            lastSourceArtifacts = sourceArtifacts
            return StoredImageArtifact(
                artifactId = "img_1",
                path = "/tmp/1.png",
                uri = "file:///tmp/1.png",
                galleryId = 1,
                mimeType = "image/png",
                width = 64,
                height = 64,
            )
        }
    }

    private class FakeModelRegistry(
        descriptor: ModelDescriptor,
        defaultId: String,
    ) : ModelRegistry {
        override val models = MutableStateFlow(listOf(descriptor))
        override val providers = MutableStateFlow<List<ModelProviderDescriptor>>(emptyList())
        override val assignments = MutableStateFlow(
            ModelAssignments(
                defaults = mapOf(
                    ModelRole.IMAGE_GENERATION to defaultId,
                    ModelRole.IMAGE_EDITING to defaultId,
                ),
            ),
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

    private fun descriptor(): ModelDescriptor = ModelDescriptor(
        id = GEN_DESCRIPTOR_ID,
        displayName = "test-double",
        source = ModelSource.Cloud(providerId = CLOUD_PROVIDER_ID.toString(), remoteModelId = "gemini"),
        capabilities = setOf(ModelCapability.IMAGE_GENERATION, ModelCapability.IMAGE_EDITING),
        lifecycle = ModelLifecycle.AVAILABLE,
    )

    private fun service(backend: StubBackend, store: StubStore): GenerationService {
        val resolver = ModelRoleResolver(FakeModelRegistry(descriptor(), GEN_DESCRIPTOR_ID))
        return GenerationService(resolver, backend, store)
    }

    @Test
    fun `generate resolves setting, invokes backend and persists`() = runBlocking {
        val backend = StubBackend()
        val store = StubStore()
        val service = service(backend, store)
        val settings = SettingsStub().settings
        val assistant = settings.getCurrentAssistant()

        val outcome = service.generate(
            settings = settings,
            assistant = assistant,
            params = ImageGenerationParams(
                model = settings.providers.first().models.first(),
                prompt = "a red apple",
            ),
        ) as GenerationResult.Success

        assertEquals(1, backend.generateCalls)
        assertEquals(1, outcome.artifacts.size)
        assertEquals("img_1", outcome.receipt.artifactId)
        assertEquals("a red apple", outcome.prompt)
        assertEquals("cloud", outcome.receipt.runtime)
        assertEquals("cloud", outcome.receipt.backend)
    }

    @Test
    fun `empty outcome is returned when the provider yields only partials`() = runBlocking {
        val backend = object : StubBackend() {
            override suspend fun generateImage(
                providerSetting: ProviderSetting,
                params: ImageGenerationParams,
            ): Flow<ImageGenerationItem> {
                generateCalls++
                return flowOf(
                    ImageGenerationItem(
                        payload = GeneratedImagePayload.Base64("p1", "image/png"),
                        partial = true,
                        partialImageIndex = 0,
                    ),
                )
            }
        }
        val store = StubStore()
        val service = service(backend, store)
        val settings = SettingsStub().settings

        val outcome = service.generate(
            settings = settings,
            assistant = settings.getCurrentAssistant(),
            params = ImageGenerationParams(
                model = settings.providers.first().models.first(),
                prompt = "a red apple",
            ),
        )

        assertTrue(outcome is GenerationResult.Empty)
        assertEquals("a red apple", outcome.prompt)
        assertEquals(1, backend.generateCalls)
    }

    @Test
    fun `onPartial receives partial items while finals persist`() = runBlocking {
        val backend = object : StubBackend() {
            override suspend fun generateImage(
                providerSetting: ProviderSetting,
                params: ImageGenerationParams,
            ): Flow<ImageGenerationItem> {
                generateCalls++
                return flowOf(
                    ImageGenerationItem(
                        payload = GeneratedImagePayload.Base64("p1", "image/png"),
                        partial = true,
                        partialImageIndex = 0,
                    ),
                    ImageGenerationItem(payload = GeneratedImagePayload.Base64("final", "image/png"), partial = false),
                )
            }
        }
        val store = StubStore()
        val service = service(backend, store)
        val settings = SettingsStub().settings
        val partials = mutableListOf<ImageGenerationItem>()

        val outcome = service.generate(
            settings = settings,
            assistant = settings.getCurrentAssistant(),
            params = ImageGenerationParams(
                model = settings.providers.first().models.first(),
                prompt = "a red apple",
            ),
            onPartial = { partials += it },
        ) as GenerationResult.Success

        assertEquals(1, partials.size)
        assertTrue(partials.single().partial)
        assertEquals(1, outcome.artifacts.size)
    }

    @Test
    fun `edit passes source artifacts through to persistence`() = runBlocking {
        val backend = StubBackend()
        val store = StubStore()
        val service = service(backend, store)
        val settings = SettingsStub().settings
        val assistant = settings.getCurrentAssistant()
        val source = listOf(MediaArtifactRef(artifactId = "img_0", path = "/tmp/0.png"))

        val outcome = service.edit(
            settings = settings,
            assistant = assistant,
            params = ImageEditParams(
                model = settings.providers.first().models.first(),
                prompt = "add a hat",
                images = listOf("img_0"),
            ),
            sourceArtifacts = source,
        ) as GenerationResult.Success

        assertEquals(1, backend.editCalls)
        assertEquals(1, outcome.artifacts.size)
        assertEquals(source, store.lastSourceArtifacts)
        assertEquals("img_1", outcome.receipt.artifactId)
    }

    @Test
    fun `generate rejects when assistant disables cloud image processing`() {
        val backend = StubBackend()
        val store = StubStore()
        val service = service(backend, store)
        val settings = SettingsStub().settings
        val assistant = settings.getCurrentAssistant().copy(allowCloudImageProcessing = false)

        val thrown = runCatching {
            runBlocking {
                service.generate(
                    settings = settings,
                    assistant = assistant,
                    params = ImageGenerationParams(
                        model = settings.providers.first().models.first(),
                        prompt = "a red apple",
                    ),
                )
            }
        }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        assertTrue(thrown?.message?.contains("Cloud image processing is disabled") == true)
        assertEquals(0, backend.generateCalls)
    }
}
