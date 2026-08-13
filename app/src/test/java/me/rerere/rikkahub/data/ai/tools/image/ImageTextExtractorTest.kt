package me.rerere.rikkahub.data.ai.tools.image

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.locallm.LocalRuntime
import me.rerere.rikkahub.data.datastore.Settings
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
import me.rerere.rikkahub.data.modelregistry.RegistryModelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

// Uuid-parseable descriptor ids: the production flow does
// settings.findModelById(Uuid.parse(descriptor.id)) which throws on non-UUID strings.
private val CLOUD_DESCRIPTOR_ID = "c10a4d3e-0001-4a4d-8001-000000000001"
private val LOCAL_DESCRIPTOR_ID = "110a4d3e-0002-4a4d-8002-000000000002"
private val CLOUD_PROVIDER_ID = Uuid.parse("c10a4d3e-0001-4a4d-8001-000000000003")
private val LITERT_PROVIDER_ID = Uuid.parse("11111111-aaaa-bbbb-cccc-000000000002")

class ImageTextExtractorTest {

    @Test
    fun `cloud-disabled analysis rejects before any provider call`() = runBlocking {
        val backend = RecordingBackend()
        val extractor = ImageTextExtractor(backend, registryWithRole(ModelRole.OCR, CLOUD_DESCRIPTOR_ID, cloud = true))
        val assistant = Assistant(
            allowCloudAttachmentProcessing = false,
            modelOverrides = mapOf(ModelRole.OCR to RegistryModelId(CLOUD_DESCRIPTOR_ID)),
        )
        val result = extractor.extract(
            media = ResolvedMedia("/tmp/x.png", "/tmp/x.png", "image/png", 10, false),
            assistant = assistant,
            settings = SettingsStub().settings,
        )
        assertNull(result.success)
        assertEquals("cloud_processing_blocked", result.errorCode)
        assertEquals(0, backend.calls)
    }

    @Test
    fun `no compatible model returns structured failure with recovery`() = runBlocking {
        val backend = RecordingBackend()
        val extractor = ImageTextExtractor(backend, registryWithRole(ModelRole.OCR, null))
        val result = extractor.extract(
            media = ResolvedMedia("/tmp/x.png", "/tmp/x.png", "image/png", 10, false),
            assistant = Assistant(),
            settings = SettingsStub().settings,
        )
        assertNull(result.success)
        assertEquals("no_compatible_model", result.errorCode)
        assertEquals(0, backend.calls)
    }

    @Test
    fun `local extraction proceeds and returns raw text`() = runBlocking {
        val backend = RecordingBackend(reply = "extracted: hello")
        val extractor = ImageTextExtractor(backend, registryWithRole(ModelRole.OCR, LOCAL_DESCRIPTOR_ID, cloud = false))
        val result = extractor.extract(
            media = ResolvedMedia("/tmp/x.png", "/tmp/x.png", "image/png", 10, false),
            assistant = Assistant(),
            settings = SettingsStub().settings,
        )
        assertTrue(result.success == true)
        assertEquals("extracted: hello", result.text)
        assertEquals(1, backend.calls)
    }

    private fun registryWithRole(role: ModelRole, id: String?, cloud: Boolean = false): ModelRoleResolver {
        val descriptor = id?.let {
            ModelDescriptor(
                id = it,
                displayName = "fake",
                source = if (cloud) {
                    ModelSource.Cloud(providerId = CLOUD_PROVIDER_ID.toString(), remoteModelId = "gemini")
                } else {
                    ModelSource.Local(runtime = LocalRuntime.LiteRT)
                },
                capabilities = setOf(ModelCapability.OCR),
                // A local descriptor must be READY (or installed) for ModelResolver to auto-resolve it.
                lifecycle = if (cloud) ModelLifecycle.AVAILABLE else ModelLifecycle.READY,
            )
        }
        return ModelRoleResolver(FakeModelRegistry(descriptor = descriptor, defaultId = id))
    }

    private class FakeModelRegistry(
        descriptor: ModelDescriptor?,
        defaultId: String?,
    ) : ModelRegistry {
        override val models = MutableStateFlow(descriptor?.let { listOf(it) } ?: emptyList())
        override val providers = MutableStateFlow<List<ModelProviderDescriptor>>(emptyList())
        override val assignments = MutableStateFlow(
            ModelAssignments(defaults = defaultId?.let { mapOf(ModelRole.OCR to it) } ?: emptyMap()),
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
                        Model(id = Uuid.parse(CLOUD_DESCRIPTOR_ID), modelId = "gemini", displayName = "Gemini"),
                    ),
                ),
                ProviderSetting.LiteRtLocal(
                    id = LITERT_PROVIDER_ID,
                    enabled = true,
                    models = listOf(
                        Model(id = Uuid.parse(LOCAL_DESCRIPTOR_ID), modelId = "ocr-local", displayName = "OCR Local"),
                    ),
                ),
            ),
            assistants = listOf(Assistant()),
        )
    }

    private class RecordingBackend(
        private val reply: String = "default reply",
    ) : ImageToolBackend {
        var calls = 0

        override suspend fun generateImage(providerSetting: ProviderSetting, params: ImageGenerationParams): Flow<ImageGenerationItem> {
            calls++
            return emptyFlow()
        }

        override suspend fun editImage(providerSetting: ProviderSetting, params: ImageEditParams): Flow<ImageGenerationItem> {
            calls++
            return emptyFlow()
        }

        override suspend fun generateText(
            providerSetting: ProviderSetting,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk {
            calls++
            return MessageChunk(
                id = "fake",
                model = "fake",
                choices = listOf(
                    UIMessageChoice(
                        index = 0,
                        delta = null,
                        message = UIMessage(
                            role = MessageRole.ASSISTANT,
                            parts = listOf(UIMessagePart.Text(reply)),
                        ),
                        finishReason = null,
                    ),
                ),
            )
        }
    }
}
