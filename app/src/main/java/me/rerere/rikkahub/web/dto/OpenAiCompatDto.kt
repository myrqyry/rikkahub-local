package me.rerere.rikkahub.web.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OpenAiChatCompletionRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    val n: Int = 1,
    val tools: JsonElement? = null,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null,
    @SerialName("response_format") val responseFormat: JsonElement? = null,
    val stop: JsonElement? = null,
)

@Serializable
data class OpenAiChatMessage(
    val role: String,
    val content: JsonElement,
)

@Serializable
data class OpenAiModelsResponse(
    val `object`: String = "list",
    val data: List<OpenAiModelObject>,
)

@Serializable
data class OpenAiModelObject(
    val id: String,
    val `object`: String = "model",
    val created: Long = 0,
    @SerialName("owned_by") val ownedBy: String = "rikkahub-local",
)

@Serializable
data class OpenAiChatCompletionResponse(
    val id: String,
    val `object`: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<OpenAiChatChoice>,
)

@Serializable
data class OpenAiChatChoice(
    val index: Int,
    val message: OpenAiAssistantMessage,
    @SerialName("finish_reason") val finishReason: String,
)

@Serializable
data class OpenAiAssistantMessage(
    val role: String = "assistant",
    val content: String,
)

@Serializable
data class OpenAiStreamChunk(
    val id: String,
    val `object`: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<OpenAiStreamChoice>,
)

@Serializable
data class OpenAiStreamChoice(
    val index: Int,
    val delta: OpenAiDelta,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null,
)

@Serializable
data class OpenAiErrorResponse(
    val error: OpenAiError,
)

@Serializable
data class OpenAiError(
    val message: String,
    val type: String,
    val param: String? = null,
    val code: String? = null,
)
