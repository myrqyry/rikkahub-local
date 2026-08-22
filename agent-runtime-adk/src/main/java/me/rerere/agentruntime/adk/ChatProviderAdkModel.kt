package me.rerere.agentruntime.adk

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model as AdkModel
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model as RikkaModel
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

fun Content.toUIMessage(): UIMessage {
    val role = when (role) {
        Role.USER -> MessageRole.USER
        Role.MODEL -> MessageRole.ASSISTANT
        Role.SYSTEM -> MessageRole.SYSTEM
        "tool" -> MessageRole.TOOL
        else -> MessageRole.USER
    }
    val parts = parts.mapNotNull { part -> part.text?.let { UIMessagePart.Text(it) } }
    return UIMessage(role = role, parts = parts)
}

fun MessageChunk.toLlmResponse(): LlmResponse {
    val choice = choices.firstOrNull()
    val text = (choice?.delta ?: choice?.message)
        ?.parts
        ?.filterIsInstance<UIMessagePart.Text>()
        ?.joinToString("") { it.text }
        .orEmpty()
    return LlmResponse(
        content = if (text.isEmpty()) null else Content(role = Role.MODEL, parts = listOf(Part(text = text))),
        partial = choice?.finishReason == null,
        finishReason = when (choice?.finishReason) {
            "stop" -> FinishReason.STOP
            else -> null
        },
        modelVersion = model,
    )
}

class ChatProviderAdkModel<S : ProviderSetting>(
    private val provider: Provider<S>,
    private val providerSetting: S,
    private val model: RikkaModel,
    override val name: String,
) : AdkModel {

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        val messages = request.contents.map { it.toUIMessage() }
        val system = request.config.systemInstruction?.let { it.toUIMessage() }
        val allMessages = buildList {
            if (system != null) add(system)
            addAll(messages)
        }
        val params = TextGenerationParams(
            model = model,
            temperature = request.config.temperature,
            topP = request.config.topP,
            maxTokens = request.config.maxOutputTokens,
        )
        if (stream) {
            provider.streamText(providerSetting, allMessages, params).collect { chunk ->
                emit(chunk.toLlmResponse())
            }
        } else {
            emit(provider.generateText(providerSetting, allMessages, params).toLlmResponse())
        }
    }
}
