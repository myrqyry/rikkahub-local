package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.ClaudeProvider
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderReasoningEffortRequestTest {
    private fun model(efforts: List<String>) = Model(
        modelId = "test-reasoning",
        abilities = listOf(ModelAbility.REASONING),
        reasoningEfforts = efforts,
    )

    private fun responseRequest(efforts: List<String>, level: ReasoningLevel): JsonObject =
        ResponseAPI(OkHttpClient()).buildRequestBody(
            ProviderSetting.OpenAI(),
            listOf(UIMessage.user("hello")),
            TextGenerationParams(model = model(efforts), reasoningLevel = level),
            false,
        )

    private fun chatRequest(efforts: List<String>, level: ReasoningLevel, endpoint: String): JsonObject {
        val api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest", List::class.java,
            TextGenerationParams::class.java, ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        ).also { it.isAccessible = true }
        return method.invoke(
            api, listOf(UIMessage.user("hello")),
            TextGenerationParams(model = model(efforts), reasoningLevel = level),
            ProviderSetting.OpenAI(baseUrl = endpoint, promptCaching = false), false,
        ) as JsonObject
    }

    private fun claudeRequest(efforts: List<String>, level: ReasoningLevel): JsonObject {
        val api = ClaudeProvider(OkHttpClient())
        val method = ClaudeProvider::class.java.getDeclaredMethod(
            "buildMessageRequest", ProviderSetting.Claude::class.java,
            List::class.java, TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
        ).also { it.isAccessible = true }
        return method.invoke(
            api, ProviderSetting.Claude(promptCaching = false),
            listOf(UIMessage.user("hello")),
            TextGenerationParams(model = model(efforts), reasoningLevel = level),
            false,
        ) as JsonObject
    }

    @Test fun responsesApiClampsXhighToAdvertisedHigh() {
        val request = responseRequest(listOf("low", "medium", "high"), ReasoningLevel.XHIGH)
        assertEquals("high", request["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
    }

    @Test fun responsesApiOmitsEffortInsteadOfUpshiftingLowToHigh() {
        val request = responseRequest(listOf("high"), ReasoningLevel.LOW)
        assertFalse(request["reasoning"]!!.jsonObject.containsKey("effort"))
    }

    @Test fun openRouterClampsXhighToAdvertisedHigh() {
        val request = chatRequest(
            listOf("low", "medium", "high"), ReasoningLevel.XHIGH,
            "https://openrouter.ai/api/v1"
        )
        assertEquals("high", request["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
    }

    @Test fun openRouterFallsBackToAutomaticThinkingWhenNoSafeEffortExists() {
        val request = chatRequest(listOf("high"), ReasoningLevel.LOW, "https://openrouter.ai/api/v1")
        val reasoning = request["reasoning"]!!.jsonObject
        assertFalse(reasoning.containsKey("effort"))
        assertTrue(reasoning["enabled"]!!.jsonPrimitive.boolean)
    }

    @Test fun openAiChatUsesExplicitNoneWhenModelAdvertisesItInsteadOfLow() {
        val request = chatRequest(listOf("none", "low", "high"), ReasoningLevel.OFF, "https://api.openai.com/v1")
        assertEquals("none", request["reasoning_effort"]!!.jsonPrimitive.content)
    }

    @Test fun openAiChatUsesAdvertisedLevelsInsteadOfHardcodedXhigh() {
        val request = chatRequest(listOf("low", "high"), ReasoningLevel.XHIGH, "https://api.openai.com/v1")
        assertEquals("high", request["reasoning_effort"]!!.jsonPrimitive.content)
    }

    @Test fun claudeClampsXhighToAdvertisedHighWithoutChangingAdaptiveMode() {
        val request = claudeRequest(listOf("low", "medium", "high"), ReasoningLevel.XHIGH)
        assertEquals("adaptive", request["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("high", request["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
    }

    @Test fun claudeMapsAdvertisedMaxEffortWhenXhighIsRequested() {
        val request = claudeRequest(listOf("low", "high", "max"), ReasoningLevel.XHIGH)
        assertEquals("max", request["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
    }

    @Test fun claudeOmitsUnsupportedExplicitEffortWhenNoLowerModeExists() {
        val request = claudeRequest(listOf("high"), ReasoningLevel.LOW)
        assertEquals("adaptive", request["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertNull(request["output_config"])
    }

    @Test fun unsetMetadataRetainsExistingXhighBehavior() {
        val request = responseRequest(emptyList(), ReasoningLevel.XHIGH)
        assertEquals("xhigh", request["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
    }
}
