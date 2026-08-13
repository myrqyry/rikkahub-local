package me.rerere.rikkahub.data.ai.tools.image

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.MessageChunk
import me.rerere.locallm.ocr.PpOcrEngine
import me.rerere.rikkahub.data.ai.tools.image.ImageTextExtractionResult
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
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class LocalOcrBranchTest {

    private class FakeBackend : ImageToolBackend {
        var generateTextCalls = 0
        override suspend fun generateImage(
            providerSetting: ProviderSetting,
            params: ImageGenerationParams,
        ): Flow<ImageGenerationItem> = flowOf()

        override suspend fun editImage(
            providerSetting: ProviderSetting,
            params: ImageEditParams,
        ): Flow<ImageGenerationItem> = flowOf()

        override suspend fun generateText(
            providerSetting: ProviderSetting,
            messages: List<me.rerere.ai.ui.UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk {
            generateTextCalls++
            return MessageChunk(id = "fake", model = "fake", choices = emptyList())
        }
    }

    private class FakeRegistry(private val descriptor: ModelDescriptor) : ModelRegistry {
        override val models = MutableStateFlow(listOf(descriptor))
        override val providers = MutableStateFlow<List<ModelProviderDescriptor>>(emptyList())
        override val assignments = MutableStateFlow(
            ModelAssignments(defaults = mapOf(ModelRole.OCR to descriptor.id)),
        )
        override suspend fun refreshProvider(providerId: String) = Unit
        override suspend fun setCapabilityEnabled(modelId: String, capability: ModelCapability, enabled: Boolean) = Unit
        override suspend fun assign(role: ModelRole, modelId: String?) = Unit
        override suspend fun install(modelId: String) = Unit
        override suspend fun remove(modelId: String) = Unit
        override suspend fun rename(modelId: String, newDisplayName: String) = Unit
    }

    private class FakeEngine : PpOcrEngine() {
        override suspend fun recognize(imagePath: String, detPath: String, recPath: String): String = "HELLO"
    }

    @Test
    fun `local task ocr provider routes to engine and skips cloud generateText`() = runBlocking {
        val modelUuid = Uuid.random()
        val providerUuid = Uuid.random()
        val descriptor = ModelDescriptor(
            id = modelUuid.toString(),
            displayName = "Local Task OCR",
            source = ModelSource.Cloud(providerId = "task_ocr_local", remoteModelId = "ocr"),
            capabilities = setOf(ModelCapability.OCR),
            enabledCapabilities = setOf(ModelCapability.OCR),
            lifecycle = ModelLifecycle.READY,
            providerEnabled = true,
        )
        val taskOcr = ProviderSetting.TaskOcrLocal(
            id = providerUuid,
            name = "Local Task OCR",
            detModelPath = "/det.tflite",
            recModelPath = "/rec.tflite",
        ).copy(models = listOf(Model(id = modelUuid)))
        val settings = Settings(providers = listOf(taskOcr))
        val resolver = ModelRoleResolver(FakeRegistry(descriptor))
        val backend = FakeBackend()
        val extractor = ImageTextExtractor(backend, resolver, ocrEngine = { FakeEngine() })

        val result: ImageTextExtractionResult = extractor.extract(
            media = ResolvedMedia(
                stablePath = "/tmp/img.png",
                originalReference = "file:///tmp/img.png",
                mimeType = "image/png",
                sizeBytes = 0L,
                temporary = false,
            ),
            assistant = Assistant(),
            settings = settings,
        )

        assertEquals(true, result.success)
        assertEquals("HELLO", result.text)
        assertEquals("ocr", result.processing)
        assertEquals(0, backend.generateTextCalls)
    }

    @Test
    fun `local task ocr engine failure maps to provider_failed without cloud fallback`() = runBlocking {
        val modelUuid = Uuid.random()
        val providerUuid = Uuid.random()
        val descriptor = ModelDescriptor(
            id = modelUuid.toString(),
            displayName = "Local Task OCR",
            source = ModelSource.Cloud(providerId = "task_ocr_local", remoteModelId = "ocr"),
            capabilities = setOf(ModelCapability.OCR),
            enabledCapabilities = setOf(ModelCapability.OCR),
            lifecycle = ModelLifecycle.READY,
            providerEnabled = true,
        )
        val taskOcr = ProviderSetting.TaskOcrLocal(
            id = providerUuid,
            detModelPath = "/det.tflite",
            recModelPath = "/rec.tflite",
        ).copy(models = listOf(Model(id = modelUuid)))
        val settings = Settings(providers = listOf(taskOcr))
        val resolver = ModelRoleResolver(FakeRegistry(descriptor))
        val backend = FakeBackend()
        val extractor = ImageTextExtractor(backend, resolver, ocrEngine = { FailEngine() })

        val result: ImageTextExtractionResult = extractor.extract(
            media = ResolvedMedia(
                stablePath = "/tmp/img.png",
                originalReference = "file:///tmp/img.png",
                mimeType = "image/png",
                sizeBytes = 0L,
                temporary = false,
            ),
            assistant = Assistant(),
            settings = settings,
        )

        assertEquals(null, result.success)
        assertEquals("provider_failed", result.errorCode)
        assertEquals(0, backend.generateTextCalls)
    }

    private class FailEngine : PpOcrEngine() {
        override suspend fun recognize(imagePath: String, detPath: String, recPath: String): String =
            throw me.rerere.locallm.ocr.PpOcrEngineException("detection model not found: $detPath")
    }

    private class EmptyRegistry : ModelRegistry {
        override val models = MutableStateFlow<List<ModelDescriptor>>(emptyList())
        override val providers = MutableStateFlow<List<ModelProviderDescriptor>>(emptyList())
        override val assignments = MutableStateFlow(ModelAssignments())
        override suspend fun refreshProvider(providerId: String) = Unit
        override suspend fun setCapabilityEnabled(modelId: String, capability: ModelCapability, enabled: Boolean) = Unit
        override suspend fun assign(role: ModelRole, modelId: String?) = Unit
        override suspend fun install(modelId: String) = Unit
        override suspend fun remove(modelId: String) = Unit
        override suspend fun rename(modelId: String, newDisplayName: String) = Unit
    }

    @Test
    fun `no compatible model still yields no_compatible_model`() = runBlocking {
        val resolver = ModelRoleResolver(EmptyRegistry())
        val backend = FakeBackend()
        val extractor = ImageTextExtractor(backend, resolver, ocrEngine = { FakeEngine() })

        val result: ImageTextExtractionResult = extractor.extract(
            media = ResolvedMedia(
                stablePath = "/tmp/img.png",
                originalReference = "file:///tmp/img.png",
                mimeType = "image/png",
                sizeBytes = 0L,
                temporary = false,
            ),
            assistant = Assistant(),
            settings = Settings(),
        )

        assertEquals(null, result.success)
        assertEquals("no_compatible_model", result.errorCode)
        assertEquals(0, backend.generateTextCalls)
    }
}
