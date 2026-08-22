package me.rerere.agentruntime

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model as AdkModel
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.rerere.agentruntime.adk.ChatProviderAdkModel
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.provider.Model as RikkaModel
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleAgentRuntimeTest {

    private val rikkaModel = RikkaModel(modelId = "local/qwen", displayName = "Qwen")

    private class FakeProvider : Provider<ProviderSetting.LlamaCppLocal> {
        override suspend fun listModels(providerSetting: ProviderSetting.LlamaCppLocal): List<RikkaModel> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.LlamaCppLocal,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk = MessageChunk(
            id = "g1",
            model = "local/qwen",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("one shot"))),
                    finishReason = "stop",
                ),
            ),
        )

        override suspend fun streamText(
            providerSetting: ProviderSetting.LlamaCppLocal,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): Flow<MessageChunk> = listOf(
            MessageChunk(
                id = "s1",
                model = "local/qwen",
                choices = listOf(UIMessageChoice(0, delta = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Hel"))), message = null, finishReason = null)),
            ),
            MessageChunk(
                id = "s2",
                model = "local/qwen",
                choices = listOf(UIMessageChoice(0, delta = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("lo"))), message = null, finishReason = null)),
            ),
            MessageChunk(
                id = "s3",
                model = "local/qwen",
                choices = listOf(UIMessageChoice(0, delta = null, message = null, finishReason = "stop")),
            ),
        ).asFlow()

        override suspend fun generateImage(providerSetting: ProviderSetting, params: ImageGenerationParams): Flow<ImageGenerationItem> =
            error("n/a")
    }

    private fun adkModel(): AdkModel = ChatProviderAdkModel(
        name = "local/qwen",
        provider = FakeProvider(),
        providerSetting = ProviderSetting.LlamaCppLocal(),
        model = rikkaModel,
    )

    @Test
    fun `run streams model text through ADK into AgentEvent Text`() = runBlocking {
        val runtime = SimpleAgentRuntime()
        val assistant = AssistantDefinition(name = "demo", model = adkModel(), systemPrompt = "be brief")

        val events = runtime.run(assistant, "hi").toList()

        assertTrue("expected at least one event, got ${events.size}", events.isNotEmpty())
        val texts = events.filterIsInstance<AgentEvent.Text>()
        assertTrue("expected partial streaming deltas", texts.any { it.partial })
        assertEquals("Hello", texts.joinToString("") { it.text })
        assertTrue(
            "expected a terminal event",
            events.any { it is AgentEvent.EndOfAgent || it is AgentEvent.TurnComplete },
        )
    }

    @Test
    fun `run exposes model name on the runtime`() {
        val runtime = SimpleAgentRuntime()
        assertEquals("SimpleAgentRuntime", runtime.agentName)
    }
}
