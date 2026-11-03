package me.rerere.rikkahub.web.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.ForbiddenException
import me.rerere.rikkahub.web.NotFoundException
import java.util.UUID
import java.net.InetAddress

/** Pure model inference, deliberately separate from ChatService and tool authorization.
 * Only locally installed/registered enabled text models can be served.
 */
internal fun Route.localInferenceRoutes(
    settingsStore: SettingsStore,
    providerManager: ProviderManager,
    jwtProtected: Boolean,
) {
    val inferenceGate = Mutex()
    route("/v1") {
        get("/models") {
            if (!isLocalInferenceAccessAllowed(
                jwtProtected,
                settingsStore.settingsFlow.value.webServerLocalhostOnly,
                call.request.local.remoteHost,
            )) throw ForbiddenException("Inference requires JWT authentication or a loopback-only client and server")
            call.respond(buildJsonObject {
                put("object", "list")
                put("data", buildJsonArray {
                    localTextProviders(settingsStore.settingsFlow.value.providers).forEach { (runtime, setting) ->
                        setting.models.filter { it.type == me.rerere.ai.provider.ModelType.CHAT }.forEach { model ->
                            add(buildJsonObject {
                                put("id", "$runtime/${model.modelId}")
                                put("object", "model")
                                put("owned_by", "rikkahub-local")
                            })
                        }
                    }
                })
            })
        }
        post("/chat/completions") {
            if (!isLocalInferenceAccessAllowed(
                jwtProtected,
                settingsStore.settingsFlow.value.webServerLocalhostOnly,
                call.request.local.remoteHost,
            )) throw ForbiddenException("Inference requires JWT authentication or a loopback-only client and server")
            val request = parseLocalCompletionRequest(call.receive<JsonObject>())
            val runtime = request.model.substringBefore('/')
            val name = request.model.substringAfter('/')
            val setting = localTextProviders(settingsStore.settingsFlow.value.providers)
                .firstOrNull { it.first == runtime }?.second
                ?: throw NotFoundException("Local runtime not enabled")
            val model = setting.models.firstOrNull {
                it.modelId == name && it.type == me.rerere.ai.provider.ModelType.CHAT
            } ?: throw NotFoundException("Local model not configured")
            val params = TextGenerationParams(
                model = model,
                maxTokens = request.maxTokens,
                temperature = request.temperature,
                topP = request.topP,
                tools = emptyList(),
            )
            val id = "chatcmpl-${UUID.randomUUID()}"
            val created = System.currentTimeMillis() / 1000
            val provider = providerManager.getProviderByType(setting)

            if (request.stream) {
                call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                call.respondTextWriter(contentType = ContentType.parse("text/event-stream")) {
                    inferenceGate.withLock {
                        write("data: ${completionDelta(id, request.model, created, roleOnly = true)}\n\n")
                        flush()
                        provider.streamText(setting, request.messages, params).collect { chunk ->
                            val content = chunk.choices.firstOrNull()?.delta?.parts.orEmpty()
                                .filterIsInstance<UIMessagePart.Text>()
                                .joinToString("") { it.text }
                            if (content.isNotEmpty()) {
                                write("data: ${completionDelta(id, request.model, created, content = content)}\n\n")
                                flush()
                            }
                        }
                        write("data: ${completionDelta(id, request.model, created, finish = "stop")}\n\n")
                        write("data: [DONE]\n\n")
                        flush()
                    }
                }
            } else {
                val answer = inferenceGate.withLock { provider.generateText(setting, request.messages, params) }
                val message = answer.choices.firstOrNull()?.message
                    ?: throw IllegalStateException("The local model returned no assistant message")
                val text = message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
                call.respond(completionResponse(id, request.model, created, text))
            }
        }
    }
}

/** JWT authentication is installed by WebApiModule, if enabled.
 * Without JWT, the server must be configured as loopback-only (ADB tunnel).
 */
/** The route-level JWT gate is fixed when WebApiModule starts. If JWT is
 * switched on in settings but routes have not restarted, fail closed unless
 * loopback *and* the configured bind mode is local-only.
 */
internal fun isLocalInferenceAccessAllowed(
    jwtProtectedAtStartup: Boolean,
    configuredLoopbackOnly: Boolean,
    remoteHost: String,
): Boolean {
    if (jwtProtectedAtStartup) return true // outer authenticate("auth-jwt") gate
    if (!configuredLoopbackOnly) return false
    return runCatching { InetAddress.getByName(remoteHost).isLoopbackAddress }.getOrDefault(false)
}

private fun localTextProviders(settings: List<ProviderSetting>): List<Pair<String, ProviderSetting>> =
    settings.filter { it.enabled }.mapNotNull {
        when (it) {
            is ProviderSetting.LiteRtLocal -> "litert" to it
            is ProviderSetting.LlamaCppLocal -> "llamacpp" to it
            else -> null
        }
    }

internal data class LocalCompletionRequest(
    val model: String,
    val messages: List<UIMessage>,
    val stream: Boolean,
    val maxTokens: Int?,
    val temperature: Float?,
    val topP: Float?,
)

/** Unsupported tools/multimodal formats are rejected, never silently discarded. */
internal fun parseLocalCompletionRequest(body: JsonObject): LocalCompletionRequest {
    val model = (body["model"] as? JsonPrimitive)?.contentOrNull
        ?.takeIf { it.length in 1..200 && '/' in it }
        ?: throw BadRequestException("A local model ID is required")
    if ((body["tools"] != null && body["tools"] != JsonArray(emptyList())) ||
        body["tool_calls"] != null || body["functions"] != null ||
        body["function_call"] != null || body["response_format"] != null ||
        body["parallel_tool_calls"] != null || body["stop"] != null ||
        (body["tool_choice"] != null && body["tool_choice"] != JsonPrimitive("none"))) {
        throw BadRequestException("Unsupported inference option (text only, no tool calls)")
    }
    if (body["n"] != null && (body["n"] as? JsonPrimitive)?.intOrNull != 1) {
        throw BadRequestException("Only one completion is supported")
    }
    val items = body["messages"] as? JsonArray
        ?: throw BadRequestException("messages must be an array")
    if (items.isEmpty() || items.size > 64) throw BadRequestException("Expected 1 to 64 messages")
    var total = 0
    val messages = items.map { entry ->
        val data = entry as? JsonObject ?: throw BadRequestException("Malformed message")
        if (data["tool_calls"] != null || data["function_call"] != null) {
            throw BadRequestException("Historical tool calls are not supported")
        }
        val role = when ((data["role"] as? JsonPrimitive)?.contentOrNull) {
            "system", "developer" -> MessageRole.SYSTEM
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            else -> throw BadRequestException("Unsupported message role")
        }
        val content = (data["content"] as? JsonPrimitive)?.contentOrNull
            ?: throw BadRequestException("Only plain text message content is supported")
        total += content.length
        if (total > 24_000) throw BadRequestException("Conversation exceeds local input budget")
        UIMessage(role = role, parts = listOf(UIMessagePart.Text(content)))
    }
    val limit = body["max_tokens"] ?: body["max_completion_tokens"]
    val max = if (limit == null) null else
        (limit as? JsonPrimitive)?.intOrNull?.takeIf { it in 1..4096 }
            ?: throw BadRequestException("Invalid max_tokens")
    val streaming = if (body["stream"] == null) false else
        (body["stream"] as? JsonPrimitive)?.booleanOrNull
            ?: throw BadRequestException("stream must be boolean")
    fun optionalFloat(key: String, min: Float, max: Float): Float? {
        val value = body[key] ?: return null
        return (value as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it in min..max }
            ?: throw BadRequestException("Invalid $key")
    }
    return LocalCompletionRequest(
        model, messages, streaming, max,
        optionalFloat("temperature", 0f, 2f),
        optionalFloat("top_p", 0f, 1f),
    )
}

internal fun completionResponse(id: String, model: String, created: Long, text: String): JsonObject =
    buildJsonObject {
        put("id", id)
        put("object", "chat.completion")
        put("created", created)
        put("model", model)
        put("choices", buildJsonArray {
            add(buildJsonObject {
                put("index", 0)
                put("message", buildJsonObject { put("role", "assistant"); put("content", text) })
                put("finish_reason", "stop")
            })
        })
    }

internal fun completionDelta(
    id: String,
    model: String,
    created: Long,
    content: String = "",
    roleOnly: Boolean = false,
    finish: String? = null,
): JsonObject = buildJsonObject {
    put("id", id)
    put("object", "chat.completion.chunk")
    put("created", created)
    put("model", model)
    put("choices", buildJsonArray {
        add(buildJsonObject {
            put("index", 0)
            put("delta", buildJsonObject {
                if (roleOnly) put("role", "assistant")
                if (content.isNotEmpty()) put("content", content)
            })
            put("finish_reason", finish?.let(::JsonPrimitive) ?: JsonNull)
        })
    })
}
