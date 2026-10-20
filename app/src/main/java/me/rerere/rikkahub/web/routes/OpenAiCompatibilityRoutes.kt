package me.rerere.rikkahub.web.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.web.dto.OpenAiAssistantMessage
import me.rerere.rikkahub.web.dto.OpenAiChatChoice
import me.rerere.rikkahub.web.dto.OpenAiChatCompletionRequest
import me.rerere.rikkahub.web.dto.OpenAiChatCompletionResponse
import me.rerere.rikkahub.web.dto.OpenAiChatMessage
import me.rerere.rikkahub.web.dto.OpenAiDelta
import me.rerere.rikkahub.web.dto.OpenAiError
import me.rerere.rikkahub.web.dto.OpenAiErrorResponse
import me.rerere.rikkahub.web.dto.OpenAiModelObject
import me.rerere.rikkahub.web.dto.OpenAiModelsResponse
import me.rerere.rikkahub.web.dto.OpenAiStreamChoice
import me.rerere.rikkahub.web.dto.OpenAiStreamChunk
import java.util.UUID

private const val OPENAI_OWNER = "rikkahub-local"

private class OpenAiRequestException(message: String) : IllegalArgumentException(message)

private data class LocalChatTarget(
    val exposedId: String,
    val setting: ProviderSetting,
    val provider: Provider<ProviderSetting>,
    val model: Model,
)

fun Route.openAiCompatibilityRoutes(
    settingsStore: SettingsStore,
    providerManager: ProviderManager,
) {
    route("/v1") {
        get("/models") {
            val models = discoverLocalChatModels(settingsStore, providerManager)
                .map { target ->
                    OpenAiModelObject(
                        id = target.exposedId,
                        ownedBy = OPENAI_OWNER,
                    )
                }
            call.respond(OpenAiModelsResponse(data = models))
        }

        post("/chat/completions") {
            val request = try {
                call.receive<OpenAiChatCompletionRequest>()
            } catch (t: Throwable) {
                call.respondOpenAiError(
                    status = HttpStatusCode.BadRequest,
                    message = "Invalid OpenAI chat-completions request: ${t.message ?: "malformed JSON"}",
                )
                return@post
            }

            val prepared = try {
                prepareRequest(request, settingsStore, providerManager)
            } catch (e: OpenAiRequestException) {
                call.respondOpenAiError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                return@post
            } catch (e: NoSuchElementException) {
                call.respondOpenAiError(
                    HttpStatusCode.NotFound,
                    e.message ?: "Requested local model was not found",
                    type = "invalid_request_error",
                    param = "model",
                    code = "model_not_found",
                )
                return@post
            }

            val completionId = "chatcmpl-${UUID.randomUUID()}"
            val created = System.currentTimeMillis() / 1000

            if (request.stream) {
                call.response.header(HttpHeaders.CacheControl, "no-cache")
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    val roleChunk = OpenAiStreamChunk(
                        id = completionId,
                        created = created,
                        model = prepared.target.exposedId,
                        choices = listOf(
                            OpenAiStreamChoice(
                                index = 0,
                                delta = OpenAiDelta(role = "assistant"),
                            )
                        ),
                    )
                    writeOpenAiEvent(roleChunk)

                    var finishReason: String? = null
                    try {
                        prepared.target.provider
                            .streamText(
                                prepared.setting,
                                prepared.messages,
                                prepared.params,
                            )
                            .collect { chunk ->
                                val choice = chunk.choices.firstOrNull() ?: return@collect
                                val text = choice.delta
                                    ?.parts
                                    ?.filterIsInstance<UIMessagePart.Text>()
                                    ?.joinToString(separator = "") { it.text }
                                    .orEmpty()
                                if (text.isNotEmpty()) {
                                    writeOpenAiEvent(
                                        OpenAiStreamChunk(
                                            id = completionId,
                                            created = created,
                                            model = prepared.target.exposedId,
                                            choices = listOf(
                                                OpenAiStreamChoice(
                                                    index = 0,
                                                    delta = OpenAiDelta(content = text),
                                                )
                                            ),
                                        )
                                    )
                                }
                                if (!choice.finishReason.isNullOrBlank()) {
                                    finishReason = choice.finishReason
                                }
                            }

                        writeOpenAiEvent(
                            OpenAiStreamChunk(
                                id = completionId,
                                created = created,
                                model = prepared.target.exposedId,
                                choices = listOf(
                                    OpenAiStreamChoice(
                                        index = 0,
                                        delta = OpenAiDelta(),
                                        finishReason = finishReason ?: "stop",
                                    )
                                ),
                            )
                        )
                    } catch (t: Throwable) {
                        write(
                            "data: ${JsonInstant.encodeToString(openAiError(t.message ?: "Inference failed", "server_error"))}\n\n"
                        )
                    } finally {
                        write("data: [DONE]\n\n")
                        flush()
                    }
                }
            } else {
                val text = StringBuilder()
                var finishReason: String? = null
                try {
                    prepared.target.provider
                        .streamText(
                            prepared.setting,
                            prepared.messages,
                            prepared.params,
                        )
                        .collect { chunk ->
                            val choice = chunk.choices.firstOrNull() ?: return@collect
                            choice.delta
                                ?.parts
                                ?.filterIsInstance<UIMessagePart.Text>()
                                ?.forEach { text.append(it.text) }
                            if (!choice.finishReason.isNullOrBlank()) {
                                finishReason = choice.finishReason
                            }
                        }
                } catch (t: Throwable) {
                    call.respondOpenAiError(
                        HttpStatusCode.InternalServerError,
                        t.message ?: "Inference failed",
                        type = "server_error",
                    )
                    return@post
                }

                call.respond(
                    OpenAiChatCompletionResponse(
                        id = completionId,
                        created = created,
                        model = prepared.target.exposedId,
                        choices = listOf(
                            OpenAiChatChoice(
                                index = 0,
                                message = OpenAiAssistantMessage(content = text.toString()),
                                finishReason = finishReason ?: "stop",
                            )
                        ),
                    )
                )
            }
        }
    }
}

private data class PreparedOpenAiRequest(
    val target: LocalChatTarget,
    val setting: ProviderSetting,
    val messages: List<UIMessage>,
    val params: TextGenerationParams,
)

private suspend fun prepareRequest(
    request: OpenAiChatCompletionRequest,
    settingsStore: SettingsStore,
    providerManager: ProviderManager,
): PreparedOpenAiRequest {
    if (request.model.isBlank()) throw OpenAiRequestException("model must not be blank")
    if (request.messages.isEmpty()) throw OpenAiRequestException("messages must not be empty")
    if (request.n != 1) throw OpenAiRequestException("Only n=1 is supported")
    if (request.tools != null) throw OpenAiRequestException("OpenAI tool calls are not supported by this endpoint yet")
    if (request.toolChoice != null) throw OpenAiRequestException("tool_choice is not supported by this endpoint yet")
    if (request.responseFormat != null) throw OpenAiRequestException("response_format is not supported by this endpoint yet")
    if (request.stop != null) throw OpenAiRequestException("stop sequences are not supported by this endpoint yet")
    request.temperature?.let {
        if (it < 0.0) throw OpenAiRequestException("temperature must be >= 0")
    }
    request.topP?.let {
        if (it !in 0.0..1.0) throw OpenAiRequestException("top_p must be between 0 and 1")
    }
    val maxTokens = request.maxCompletionTokens ?: request.maxTokens
    maxTokens?.let {
        if (it <= 0) throw OpenAiRequestException("max_tokens must be > 0")
    }

    val targets = discoverLocalChatModels(settingsStore, providerManager)
    val target = targets.firstOrNull { it.exposedId == request.model }
        ?: targets.filter { it.model.modelId == request.model }
            .singleOrNull()
        ?: throw NoSuchElementException("Local model '${request.model}' is not available")

    val setting = when (val original = target.setting) {
        is ProviderSetting.LiteRtLocal -> original.copy(
            temperature = request.temperature ?: original.temperature,
            topP = request.topP ?: original.topP,
        )
        is ProviderSetting.LlamaCppLocal -> original.copy(
            temperature = request.temperature ?: original.temperature,
            topP = request.topP ?: original.topP,
        )
        else -> original
    }

    val messages = request.messages.map(::openAiMessageToUiMessage)
    return PreparedOpenAiRequest(
        target = target,
        setting = setting,
        messages = messages,
        params = TextGenerationParams(
            model = target.model,
            temperature = request.temperature?.toFloat(),
            topP = request.topP?.toFloat(),
            maxTokens = maxTokens,
            customHeaders = target.model.customHeaders,
            customBody = target.model.customBodies,
        ),
    )
}

private suspend fun discoverLocalChatModels(
    settingsStore: SettingsStore,
    providerManager: ProviderManager,
): List<LocalChatTarget> {
    val settings = settingsStore.settingsFlow.value
    val targets = mutableListOf<LocalChatTarget>()
    for (setting in settings.providers) {
        if (!setting.enabled) continue
        if (setting !is ProviderSetting.LiteRtLocal && setting !is ProviderSetting.LlamaCppLocal) continue

        val provider = providerManager.getProviderByType(setting)
        for (model in provider.listModels(setting)) {
            if (model.type != ModelType.CHAT || Modality.TEXT !in model.outputModalities) continue
            targets += LocalChatTarget(
                exposedId = "${localProviderPrefix(setting)}/${model.modelId}",
                setting = setting,
                provider = provider,
                model = model,
            )
        }
    }
    return targets
}

private fun localProviderPrefix(setting: ProviderSetting): String = when (setting) {
    is ProviderSetting.LiteRtLocal -> "litert"
    is ProviderSetting.LlamaCppLocal -> "llamacpp"
    else -> error("Unsupported OpenAI local provider: ${setting::class.simpleName}")
}

internal fun openAiMessageToUiMessage(message: OpenAiChatMessage): UIMessage {
    val role = when (message.role.lowercase()) {
        "system", "developer" -> MessageRole.SYSTEM
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        "tool" -> throw OpenAiRequestException("tool-role messages are not supported by this endpoint yet")
        else -> throw OpenAiRequestException("Unsupported message role '${message.role}'")
    }
    return UIMessage(
        role = role,
        parts = listOf(UIMessagePart.Text(openAiTextContent(message.content))),
    )
}

internal fun openAiTextContent(content: JsonElement): String = when (content) {
    is JsonPrimitive -> {
        if (!content.isString) throw OpenAiRequestException("Message content must be text")
        content.content
    }
    is JsonArray -> content.joinToString(separator = "") { part ->
        val obj = part as? JsonObject
            ?: throw OpenAiRequestException("Message content arrays may contain only text objects")
        val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
        if (type != "text" && type != "input_text") {
            throw OpenAiRequestException("Message content type '${type ?: "unknown"}' is not supported yet")
        }
        val text = (obj["text"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?: throw OpenAiRequestException("Text content item is missing a string 'text' field")
        text
    }
    else -> throw OpenAiRequestException("Message content must be a string or text-content array")
}

private suspend fun java.io.Writer.writeOpenAiEvent(chunk: OpenAiStreamChunk) {
    write("data: ${JsonInstant.encodeToString(chunk)}\n\n")
    flush()
}

private fun openAiError(
    message: String,
    type: String,
    param: String? = null,
    code: String? = null,
): OpenAiErrorResponse = OpenAiErrorResponse(
    error = OpenAiError(
        message = message,
        type = type,
        param = param,
        code = code,
    )
)

private suspend fun io.ktor.server.application.ApplicationCall.respondOpenAiError(
    status: HttpStatusCode,
    message: String,
    type: String = "invalid_request_error",
    param: String? = null,
    code: String? = null,
) {
    respond(status, openAiError(message, type, param, code))
}
