package me.rerere.rikkahub.data.ai.tools.image

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.locallm.ocr.PpOcrEngine
import me.rerere.locallm.ocr.PpOcrEngineException
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.modelregistry.ModelResolution
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelRoleResolver
import me.rerere.rikkahub.data.modelregistry.ModelSourcePolicy
import me.rerere.rikkahub.data.modelregistry.canProcessAttachmentWith
import me.rerere.rikkahub.data.modelregistry.isOnDevice
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.uuid.Uuid

data class ImageTextExtractionResult(
    val success: Boolean?,
    val text: String? = null,
    val language: String? = null,
    val imageRef: String,
    val modelId: String? = null,
    val processing: String? = null,
    val errorCode: String? = null,
)

/**
 * Thin adapter over [ProviderManager] so tool execution and the OCR transformer share
 * one provider-invocation surface (resolution, privacy gate, timeout live in
 * [ImageTextExtractor], not in each caller).
 */
interface ImageToolBackend {
    suspend fun generateImage(providerSetting: ProviderSetting, params: ImageGenerationParams): Flow<ImageGenerationItem>
    suspend fun editImage(providerSetting: ProviderSetting, params: ImageEditParams): Flow<ImageGenerationItem>
    suspend fun generateText(
        providerSetting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk
}

class ProviderImageToolBackend(private val providerManager: ProviderManager) : ImageToolBackend {
    override suspend fun generateImage(providerSetting: ProviderSetting, params: ImageGenerationParams): Flow<ImageGenerationItem> =
        providerManager.getProviderByType(providerSetting).generateImage(providerSetting, params)

    override suspend fun editImage(providerSetting: ProviderSetting, params: ImageEditParams): Flow<ImageGenerationItem> =
        providerManager.getProviderByType(providerSetting).editImage(providerSetting, params)

    override suspend fun generateText(
        providerSetting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk =
        providerManager.getProviderByType(providerSetting).generateText(providerSetting, messages, params)
}

/**
 * Resolves the OCR model through [ModelRoleResolver], applies the same privacy gate as
 * the chat pipeline (`canProcessAttachmentWith`), and runs the provider call under a hard
 * timeout. The OCR tool and [me.rerere.rikkahub.data.ai.transformers.OcrTransformer] both
 * delegate here so resolution/privacy/timeout stay in one place.
 */
class ImageTextExtractor(
    private val backend: ImageToolBackend,
    private val modelRoleResolver: ModelRoleResolver,
    private val ocrEngine: (() -> PpOcrEngine)? = null,
) : KoinComponent {
    suspend fun extract(
        media: ResolvedMedia,
        assistant: Assistant,
        settings: Settings,
        requireLocal: Boolean = false,
        timeoutMillis: Long = 60_000L,
    ): ImageTextExtractionResult {
        val resolved = modelRoleResolver.resolve(ModelRole.OCR, assistant, settings, ModelSourcePolicy.ANY)
        val descriptor = when (resolved) {
            is ModelResolution.Resolved -> resolved.model
            is ModelResolution.InvalidOverride -> return failure("invalid_model_override", media)
            is ModelResolution.BlockedByPolicy -> return failure("cloud_processing_blocked", media)
            ModelResolution.NoCompatibleModel -> return failure("no_compatible_model", media)
        }
        val model = settings.findModelById(Uuid.parse(descriptor.id)) ?: return failure("no_compatible_model", media)
        val provider = model.findProvider(settings.providers) ?: return failure("provider_not_found", media)
        val providerSetting = settings.providers.find { it.id == provider.id } ?: return failure("provider_not_found", media)
        if (requireLocal && !providerSetting.isOnDevice()) return failure("cloud_processing_blocked", media)
        if (!assistant.canProcessAttachmentWith(providerSetting)) return failure("cloud_processing_blocked", media)
        if (providerSetting is ProviderSetting.TaskOcrLocal) {
            return try {
                val engine = ocrEngine?.invoke() ?: get<PpOcrEngine>()
                val text = engine.recognize(
                    imagePath = media.stablePath,
                    detPath = providerSetting.detModelPath,
                    recPath = providerSetting.recModelPath,
                )
                ImageTextExtractionResult(
                    success = true,
                    text = text,
                    imageRef = media.originalReference,
                    modelId = descriptor.id,
                    processing = "ocr",
                )
            } catch (e: PpOcrEngineException) {
                failure("provider_failed", media)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure("provider_failed", media)
            }
        }
        val prompt = settings.ocrPrompt
        val params = TextGenerationParams(model = model, customHeaders = model.customHeaders, customBody = model.customBodies)
        val messages = listOf(
            UIMessage.system(prompt),
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Image(url = "file://${media.stablePath}")),
            ),
        )
        return try {
            val chunk = withTimeoutOrNull(timeoutMillis) { backend.generateText(providerSetting, messages, params) }
                ?: return failure("provider_failed", media)
            ImageTextExtractionResult(
                success = true,
                text = chunk.choices.firstOrNull()?.message?.toText(),
                imageRef = media.originalReference,
                modelId = descriptor.id,
                processing = "ocr",
            )
        } catch (e: CancellationException) {
            // Let a real cancellation (user /stop) propagate instead of turning it into a
            // fake OCR failure; OcrTransformer depends on this for cooperative cancellation.
            throw e
        } catch (e: Exception) {
            failure("provider_failed", media)
        }
    }

    private fun failure(code: String, media: ResolvedMedia) = ImageTextExtractionResult(
        success = null,
        imageRef = media.originalReference,
        errorCode = code,
    )
}
