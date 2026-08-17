package me.rerere.rikkahub.data.ai.tools.image

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.generation.GenerationService
import me.rerere.rikkahub.data.ai.tools.ToolInvocationContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.media.ImageMediaStore
import me.rerere.rikkahub.data.media.MediaArtifactRef
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.modelregistry.ModelResolution
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelRoleResolver
import me.rerere.rikkahub.data.modelregistry.ModelSourcePolicy
import me.rerere.rikkahub.data.modelregistry.canProcessAttachmentWith
import me.rerere.rikkahub.data.modelregistry.canProcessImageWith
import kotlin.uuid.Uuid

/**
 * Minimal settings surface [ImageTools] reads (mirrors `SettingsStore.settingsFlow.value`).
 * A tiny functional interface instead of the concrete [me.rerere.rikkahub.data.datastore.SettingsStore]
 * so the JVM tests can substitute it without an Android Context / DataStore; the Koin wiring bridges
 * it with `SettingsProvider { get<SettingsStore>().settingsFlow.value }`.
 */fun interface SettingsProvider {
    fun current(): Settings
}

/**
 * The four always-registered chat image tools (generate_image / edit_image / analyze_image /
 * extract_text_from_image). Core capability tools: NOT gated by [me.rerere.rikkahub.data.ai.tools.LocalToolOption]
 * — they are added unconditionally by [me.rerere.rikkahub.data.ai.tools.LocalTools.getTools].
 * Every execute body delegates to the Tasks 1-4 components ([MediaInputResolver], [ImageTextExtractor],
 * [ImageMediaStore], [ImageToolBackend]) and returns structured JSON envelopes.
 */
class ImageTools(
    private val settingsStore: SettingsProvider,
    private val modelRoleResolver: ModelRoleResolver,
    private val imageToolBackend: ImageToolBackend,
    private val imageMediaStore: ImageMediaStore,
    private val mediaInputResolver: MediaInputResolver,
    private val imageTextExtractor: ImageTextExtractor,
    private val generationService: GenerationService,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun tools(invocationContext: ToolInvocationContext = ToolInvocationContext.EMPTY): List<Tool> = listOf(
        generateImageTool(invocationContext),
        editImageTool(invocationContext),
        analyzeImageTool(invocationContext),
        extractTextTool(invocationContext),
    )

    private fun currentAssistant(ctx: ToolInvocationContext): Assistant {
        val settings = settingsStore.current()
        return ctx.callerAssistantId?.let { id ->
            runCatching { settings.getAssistantById(Uuid.parse(id)) }.getOrNull()
        } ?: settings.getCurrentAssistant()
    }

    private fun currentSettings(): Settings = settingsStore.current()

    private fun generateImageTool(ctx: ToolInvocationContext): Tool = Tool(
        name = "generate_image",
        description = """Generate one or more images from a text prompt using the assistant's image generation model. Returns a saved image reference that can be passed to edit_image, analyze_image, or extract_text_from_image.""".trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("prompt", buildJsonObject { put("type", "string"); put("description", "Text prompt describing the image to generate.") })
                    put("aspect_ratio", buildJsonObject { put("type", "string"); put("description", "Aspect ratio, one of 1:1, 16:9, 9:16. Default 1:1.") })
                    put("count", buildJsonObject { put("type", "integer"); put("description", "Number of images to generate, 1-4. Default 1.") })
                },
                required = listOf("prompt"),
            )
        },
        systemPrompt = { _, _ -> "" },
        needsApproval = { ImageToolCatalog.requiresApproval("generate_image") },
        execute = { input ->
            val obj = input.jsonObject
            val prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull ?: return@Tool errorEnvelope("invalid_image_ref", "generate_image")
            val aspect = parseAspectRatio(obj["aspect_ratio"]?.jsonPrimitive?.contentOrNull)
            val count = (obj["count"]?.jsonPrimitive?.int ?: 1).coerceIn(1, 4)
            val settings = currentSettings()
            val assistant = currentAssistant(ctx)
            val resolved = modelRoleResolver.resolve(ModelRole.IMAGE_GENERATION, assistant, settings, ModelSourcePolicy.ANY)
            val descriptor = resolved as? ModelResolution.Resolved
                ?: return@Tool resolveErrorEnvelope(resolved, "image_generation", input)
            val model = settings.findModelById(Uuid.parse(descriptor.model.id)) ?: return@Tool errorEnvelope("no_compatible_model", "generate_image", role = "image_generation")
            val provider = model.findProvider(settings.providers) ?: return@Tool errorEnvelope("provider_not_found", "generate_image")
            val providerSetting = settings.providers.find { it.id == provider.id } ?: return@Tool errorEnvelope("provider_not_found", "generate_image")
            if (!assistant.canProcessImageWith(providerSetting)) return@Tool errorEnvelope("cloud_processing_blocked", "generate_image", role = "image_generation")
            val params = ImageGenerationParams(
                model = model,
                prompt = prompt,
                numOfImages = count,
                aspectRatio = aspect,
                customHeaders = model.customHeaders,
                customBody = model.customBodies,
            )
            val parts = mutableListOf<UIMessagePart>()
            try {
                val outcome = generationService.generate(settings, assistant, params)
                if (outcome.artifacts.isEmpty()) return@Tool errorEnvelope("generation_returned_no_images", "generate_image")
                val artifacts = outcome.artifacts
                artifacts.forEach { parts.add(UIMessagePart.Image(url = it.uri)) }
                val result = ImageToolResult(
                    success = true,
                    operation = ImageOperation.IMAGE_GENERATION,
                    artifacts = artifacts,
                    modelId = descriptor.model.id,
                    providerId = providerSetting.id.toString(),
                    executionSource = descriptor.source.toString(),
                    receipt = outcome.receipt,
                )
                parts.add(UIMessagePart.Text(json.encodeToString(result)))
                parts
            } catch (e: Exception) {
                errorEnvelope("provider_failed", "generate_image")
            }
        },
    )

    private fun editImageTool(ctx: ToolInvocationContext): Tool = Tool(
        name = "edit_image",
        description = """Edit an existing image by reference using the assistant's image editing model. The image_ref can come from generate_image, a previous edit, or an image the user shared.""".trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("image_ref", buildJsonObject { put("type", "string"); put("description", "Reference to the source image (from generate_image, edit_image, or a user-shared image).") })
                    put("prompt", buildJsonObject { put("type", "string"); put("description", "Instructions describing the edit to apply to the image.") })
                },
                required = listOf("image_ref", "prompt"),
            )
        },
        systemPrompt = { _, _ -> "" },
        needsApproval = { ImageToolCatalog.requiresApproval("edit_image") },
        execute = { input ->
            val obj = input.jsonObject
            val imageRef = obj["image_ref"]?.jsonPrimitive?.contentOrNull ?: return@Tool errorEnvelope("invalid_image_ref", "edit_image")
            val prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull ?: return@Tool errorEnvelope("invalid_image_ref", "edit_image")
            val media = try {
                mediaInputResolver.resolveImage(imageRef, ctx)
            } catch (e: IllegalStateException) {
                return@Tool errorEnvelope(e.message ?: "invalid_image_ref", "edit_image")
            }
            val settings = currentSettings()
            val assistant = currentAssistant(ctx)
            val resolved = modelRoleResolver.resolve(ModelRole.IMAGE_EDITING, assistant, settings, ModelSourcePolicy.ANY)
            val descriptor = resolved as? ModelResolution.Resolved
                ?: return@Tool resolveErrorEnvelope(resolved, "image_editing", input)
            val model = settings.findModelById(Uuid.parse(descriptor.model.id)) ?: return@Tool errorEnvelope("no_compatible_model", "edit_image", role = "image_editing")
            val provider = model.findProvider(settings.providers) ?: return@Tool errorEnvelope("provider_not_found", "edit_image")
            val providerSetting = settings.providers.find { it.id == provider.id } ?: return@Tool errorEnvelope("provider_not_found", "edit_image")
            if (!assistant.canProcessImageWith(providerSetting)) return@Tool errorEnvelope("cloud_processing_blocked", "edit_image", role = "image_editing")
            val params = ImageEditParams(
                model = model,
                prompt = prompt,
                images = listOf(media.stablePath),
                customHeaders = model.customHeaders,
                customBody = model.customBodies,
            )
            val parts = mutableListOf<UIMessagePart>()
            try {
                val sourceArtifacts = listOf(MediaArtifactRef(media.originalReference, media.stablePath))
                val outcome = generationService.edit(settings, assistant, params, sourceArtifacts)
                if (outcome.artifacts.isEmpty()) return@Tool errorEnvelope("generation_returned_no_images", "edit_image")
                val artifacts = outcome.artifacts
                artifacts.forEach { parts.add(UIMessagePart.Image(url = it.uri)) }
                val result = ImageToolResult(
                    success = true,
                    operation = ImageOperation.IMAGE_EDIT,
                    artifacts = artifacts,
                    modelId = descriptor.model.id,
                    providerId = providerSetting.id.toString(),
                    executionSource = descriptor.source.toString(),
                    receipt = outcome.receipt,
                )
                parts.add(UIMessagePart.Text(json.encodeToString(result)))
                parts
            } catch (e: Exception) {
                errorEnvelope("provider_failed", "edit_image")
            }
        },
    )

    private fun analyzeImageTool(ctx: ToolInvocationContext): Tool = Tool(
        name = "analyze_image",
        description = """Analyze an image by reference with the assistant's vision model and return a text description, or an answer to a question about the image.""".trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("image_ref", buildJsonObject { put("type", "string"); put("description", "Reference to the image to analyze (from generate_image, edit_image, or a user-shared image).") })
                    put("question", buildJsonObject { put("type", "string"); put("description", "Optional question about the image. Defaults to a general description.") })
                },
                required = listOf("image_ref"),
            )
        },
        systemPrompt = { _, _ -> "" },
        needsApproval = { ImageToolCatalog.requiresApproval("analyze_image") },
        execute = { input ->
            val obj = input.jsonObject
            val imageRef = obj["image_ref"]?.jsonPrimitive?.contentOrNull ?: return@Tool errorEnvelope("invalid_image_ref", "analyze_image")
            val media = try {
                mediaInputResolver.resolveImage(imageRef, ctx)
            } catch (e: IllegalStateException) {
                return@Tool errorEnvelope(e.message ?: "invalid_image_ref", "analyze_image")
            }
            val settings = currentSettings()
            val assistant = currentAssistant(ctx)
            val resolved = modelRoleResolver.resolve(ModelRole.VISION, assistant, settings, ModelSourcePolicy.ANY)
            val descriptor = resolved as? ModelResolution.Resolved
                ?: return@Tool resolveErrorEnvelope(resolved, "vision", input)
            val model = settings.findModelById(Uuid.parse(descriptor.model.id)) ?: return@Tool errorEnvelope("no_compatible_model", "analyze_image", role = "vision")
            val provider = model.findProvider(settings.providers) ?: return@Tool errorEnvelope("provider_not_found", "analyze_image")
            val providerSetting = settings.providers.find { it.id == provider.id } ?: return@Tool errorEnvelope("provider_not_found", "analyze_image")
            if (!assistant.canProcessAttachmentWith(providerSetting)) return@Tool errorEnvelope("cloud_processing_blocked", "analyze_image", role = "vision")
            val question = obj["question"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: "Describe this image in detail."
            try {
                val chunk = withTimeoutOrNull(60_000L) {
                    imageToolBackend.generateText(
                        providerSetting,
                        listOf(
                            UIMessage(
                                role = MessageRole.USER,
                                parts = listOf(
                                    UIMessagePart.Image(url = "file://${media.stablePath}"),
                                    UIMessagePart.Text(question),
                                ),
                            ),
                        ),
                        TextGenerationParams(
                            model = model,
                            customHeaders = model.customHeaders,
                            customBody = model.customBodies,
                        ),
                    )
                } ?: return@Tool errorEnvelope("provider_failed", "analyze_image")
                val analysis = chunk.choices.firstOrNull()?.message?.toText()
                    ?: return@Tool errorEnvelope("provider_failed", "analyze_image")
                val envelope = buildJsonObject {
                    put("success", true)
                    put("analysis", analysis)
                    put("image_ref", media.originalReference)
                    put("model_id", descriptor.model.id)
                    put("processing", "vision")
                }
                listOf(UIMessagePart.Text(json.encodeToString(envelope)))
            } catch (e: Exception) {
                errorEnvelope("provider_failed", "analyze_image")
            }
        },
    )

    private fun extractTextTool(ctx: ToolInvocationContext): Tool = Tool(
        name = "extract_text_from_image",
        description = """Extract text from an image by reference using the assistant's OCR model. Returns the recognized text with an optional language hint.""".trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("image_ref", buildJsonObject { put("type", "string"); put("description", "Reference to the image to extract text from (from generate_image, edit_image, or a user-shared image).") })
                },
                required = listOf("image_ref"),
            )
        },
        systemPrompt = { _, _ -> "" },
        needsApproval = { ImageToolCatalog.requiresApproval("extract_text_from_image") },
        execute = { input ->
            val obj = input.jsonObject
            val imageRef = obj["image_ref"]?.jsonPrimitive?.contentOrNull ?: return@Tool errorEnvelope("invalid_image_ref", "extract_text_from_image")
            val media = try {
                mediaInputResolver.resolveImage(imageRef, ctx)
            } catch (e: IllegalStateException) {
                return@Tool errorEnvelope(e.message ?: "invalid_image_ref", "extract_text_from_image")
            }
            val result = imageTextExtractor.extract(media, currentAssistant(ctx), currentSettings())
            if (result.success == true) {
                val envelope = buildJsonObject {
                    put("success", true)
                    put("text", result.text ?: "")
                    put("language", result.language?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("image_ref", media.originalReference)
                    put("model_id", result.modelId ?: "")
                    put("processing", "ocr")
                }
                listOf(UIMessagePart.Text(json.encodeToString(envelope)))
            } else {
                errorEnvelope(result.errorCode ?: "provider_failed", "extract_text_from_image")
            }
        },
    )

    private fun parseAspectRatio(raw: String?): ImageAspectRatio = when (raw) {
        "16:9", "3:2", "landscape" -> ImageAspectRatio.LANDSCAPE
        "9:16", "2:3", "portrait" -> ImageAspectRatio.PORTRAIT
        else -> ImageAspectRatio.SQUARE
    }

    private fun errorEnvelope(
        code: String,
        operation: String,
        role: String? = null,
        detail: String? = null,
    ): List<UIMessagePart> = listOf(
        UIMessagePart.Text(
            json.encodeToString(
                buildJsonObject {
                    put("error", code)
                    if (role != null) put("role", role)
                    if (detail != null) put("detail", detail)
                    if (code == "no_compatible_model") {
                        put("recovery", buildJsonObject {
                            put("action", "manage_models")
                            put("tab", "image")
                            put("focus", "models")
                        })
                    }
                },
            ),
        ),
    )

    private fun resolveErrorEnvelope(resolved: ModelResolution, role: String, input: JsonElement): List<UIMessagePart> = when (resolved) {
        is ModelResolution.InvalidOverride -> errorEnvelope("invalid_model_override", role, role = role)
        is ModelResolution.BlockedByPolicy -> errorEnvelope("cloud_processing_blocked", role, role = role)
        else -> errorEnvelope("no_compatible_model", role, role = role)
    }
}
