package me.rerere.agentruntime.adk

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model as AdkModel
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.Model as RikkaModel
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatProviderAdkModelTest {

    private val rikkaModel = RikkaModel(modelId = "local/qwen", displayName = "Qwen")

    private class FakeProvider : Provider<ProviderSetting.LlamaCppLocal> {
        var receivedMessages: List<UIMessage> = emptyList()

        override suspend fun listModels(providerSetting: ProviderSetting.LlamaCppLocal): List<RikkaModel> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.LlamaCppLocal,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk {
            receivedMessages = messages
            return MessageChunk(
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
        }

        override suspend fun streamText(
            providerSetting: ProviderSetting.LlamaCppLocal,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): Flow<MessageChunk> {
            receivedMessages = messages
            return listOf(
                MessageChunk(
                    id = "s1",
                    model = "local/qwen",
                    choices = listOf(
                        UIMessageChoice(
                            index = 0,
                            delta = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Hel"))),
                            message = null,
                            finishReason = null,
                        ),
                    ),
                ),
                MessageChunk(
                    id = "s2",
                    model = "local/qwen",
                    choices = listOf(
                        UIMessageChoice(
                            index = 0,
                            delta = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("lo"))),
                            message = null,
                            finishReason = null,
                        ),
                    ),
                ),
                MessageChunk(
                    id = "s3",
                    model = "local/qwen",
                    choices = listOf(
                        UIMessageChoice(
                            index = 0,
                            delta = null,
                            message = null,
                            finishReason = "stop",
                        ),
                    ),
                ),
            ).asFlow()
        }

        override suspend fun generateImage(
            providerSetting: ProviderSetting,
            params: me.rerere.ai.provider.ImageGenerationParams,
        ): Flow<me.rerere.ai.ui.ImageGenerationItem> = error("n/a")
    }

    private val setting = ProviderSetting.LlamaCppLocal()

    @Test
    fun `implements ADK Model interface`() {
        val provider = FakeProvider()
        val model: AdkModel = ChatProviderAdkModel(
            name = "local/qwen",
            provider = provider,
            providerSetting = setting,
            model = rikkaModel,
        )
        assertEquals("local/qwen", model.name)
    }

    @Test
    fun `non-streaming generateContent returns a single completed response`() {
        val provider = FakeProvider()
        val model = ChatProviderAdkModel(
            name = "local/qwen",
            provider = provider,
            providerSetting = setting,
            model = rikkaModel,
        )
        val request = LlmRequest(
            contents = listOf(Content(role = Role.USER, parts = listOf(Part(text = "hi")))),
        )

        val responses = runBlocking { model.generateContent(request, stream = false).toList() }

        assertEquals(1, responses.size)
        val response = responses[0]
        assertEquals("one shot", response.content?.parts?.firstOrNull()?.text)
        assertEquals(FinishReason.STOP, response.finishReason)
        assertEquals("local/qwen", response.modelVersion)
    }

    @Test
    fun `streaming generateContent emits partial deltas then a final response`() {
        val provider = FakeProvider()
        val model = ChatProviderAdkModel(
            name = "local/qwen",
            provider = provider,
            providerSetting = setting,
            model = rikkaModel,
        )
        val request = LlmRequest(
            contents = listOf(Content(role = Role.USER, parts = listOf(Part(text = "hi")))),
        )

        val responses = runBlocking { model.generateContent(request, stream = true).toList() }

        assertEquals(3, responses.size)
        assertEquals("Hel", responses[0].content?.parts?.firstOrNull()?.text)
        assertTrue(responses[0].partial)
        assertEquals("lo", responses[1].content?.parts?.firstOrNull()?.text)
        val last = responses.last()
        assertNotNull(last.finishReason)
        assertEquals(FinishReason.STOP, last.finishReason)
    }

    @Test
    fun `system instruction is passed through to the provider`() {
        val provider = FakeProvider()
        val model = ChatProviderAdkModel(
            name = "local/qwen",
            provider = provider,
            providerSetting = setting,
            model = rikkaModel,
        )
        val request = LlmRequest(
            contents = listOf(Content(role = Role.USER, parts = listOf(Part(text = "hi")))),
        ).let { req ->
            req.copy(config = req.config.copy(systemInstruction = Content(role = Role.SYSTEM, parts = listOf(Part(text = "be brief")))))
        }

        runBlocking { model.generateContent(request, stream = false).toList() }

        val roles = provider.receivedMessages.map { it.role }
        assertTrue(roles.contains(MessageRole.SYSTEM))
        assertTrue(roles.contains(MessageRole.USER))
    }

    @Test
    fun `assistant role content maps to assistant UIMessage role`() {
        val content = Content(role = Role.MODEL, parts = listOf(Part(text = "answer")))
        val ui = content.toUIMessage()
        assertEquals(MessageRole.ASSISTANT, ui.role)
        assertEquals(listOf("answer"), ui.parts.filterIsInstance<UIMessagePart.Text>().map { it.text })
    }
}
