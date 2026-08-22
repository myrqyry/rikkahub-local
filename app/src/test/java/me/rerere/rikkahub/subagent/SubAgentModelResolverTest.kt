package me.rerere.rikkahub.subagent

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubAgentModelResolverTest {

    private fun chatModel(id: String, display: String) = Model(
        modelId = id,
        displayName = display,
        type = ModelType.CHAT,
    )

    private fun openAI(vararg models: Model) = ProviderSetting.OpenAI(enabled = true, models = models.toList())
    private fun google(vararg models: Model) = ProviderSetting.Google(enabled = true, models = models.toList())
    private fun disabledProvider(vararg models: Model) = ProviderSetting.OpenAI(enabled = false, models = models.toList())

    @Test
    fun `null input returns Inherit`() {
        val result = SubAgentModelResolver.resolve(null, emptyList())
        assertTrue(result is SubAgentModelResolver.Result.Inherit)
    }

    @Test
    fun `blank input returns Inherit`() {
        val result = SubAgentModelResolver.resolve("  ", emptyList())
        assertTrue(result is SubAgentModelResolver.Result.Inherit)
    }

    @Test
    fun `match by modelId case-insensitive`() {
        val m = chatModel("gpt-4o", "GPT-4o")
        val providers = listOf(openAI(m))
        val result = SubAgentModelResolver.resolve("GPT-4o", providers)
        assertTrue(result is SubAgentModelResolver.Result.Resolved)
        assertEquals("gpt-4o", (result as SubAgentModelResolver.Result.Resolved).modelId)
    }

    @Test
    fun `match by displayName case-insensitive`() {
        val m = chatModel("gpt-4o-2024-05-13", "GPT-4o")
        val providers = listOf(openAI(m))
        val result = SubAgentModelResolver.resolve("gpt-4o", providers)
        assertTrue(result is SubAgentModelResolver.Result.Resolved)
        assertEquals("gpt-4o-2024-05-13", (result as SubAgentModelResolver.Result.Resolved).modelId)
    }

    @Test
    fun `modelId match wins over displayName match`() {
        val m1 = chatModel("gpt-4o", "GPT-4o Primary")
        val m2 = chatModel("other-id", "GPT-4o Secondary")
        val providers = listOf(openAI(m1, m2))
        val result = SubAgentModelResolver.resolve("gpt-4o", providers)
        assertTrue(result is SubAgentModelResolver.Result.Resolved)
        assertEquals("gpt-4o", (result as SubAgentModelResolver.Result.Resolved).modelId)
    }

    @Test
    fun `no match returns Failed with available list`() {
        val m = chatModel("gpt-4o", "GPT-4o")
        val providers = listOf(openAI(m))
        val result = SubAgentModelResolver.resolve("claude-3-opus", providers)
        assertTrue(result is SubAgentModelResolver.Result.Failed)
        val msg = (result as SubAgentModelResolver.Result.Failed).message
        assertTrue(msg.contains("gpt-4o"))
        assertTrue(msg.contains("GPT-4o"))
    }

    @Test
    fun `multiple models with same modelId across providers resolves`() {
        val m1 = chatModel("shared-id", "Shared A")
        val m2 = chatModel("shared-id", "Shared B")
        val providers = listOf(openAI(m1), google(m2))
        val result = SubAgentModelResolver.resolve("shared-id", providers)
        assertTrue(result is SubAgentModelResolver.Result.Resolved)
        assertEquals("shared-id", (result as SubAgentModelResolver.Result.Resolved).modelId)
    }

    @Test
    fun `multiple models different modelIds same displayName returns Failed with candidates`() {
        val m1 = chatModel("id-a", "Ambiguous Name")
        val m2 = chatModel("id-b", "Ambiguous Name")
        val providers = listOf(openAI(m1), google(m2))
        val result = SubAgentModelResolver.resolve("Ambiguous Name", providers)
        assertTrue(result is SubAgentModelResolver.Result.Failed)
        val msg = (result as SubAgentModelResolver.Result.Failed).message
        assertTrue(msg.contains("id-a"))
        assertTrue(msg.contains("id-b"))
    }

    @Test
    fun `disabled provider models are ignored`() {
        val m = chatModel("gpt-4o", "GPT-4o")
        val providers = listOf(disabledProvider(m))
        val result = SubAgentModelResolver.resolve("gpt-4o", providers)
        assertTrue(result is SubAgentModelResolver.Result.Failed)
    }

    @Test
    fun `empty providers list returns Failed`() {
        val result = SubAgentModelResolver.resolve("any-model", emptyList())
        assertTrue(result is SubAgentModelResolver.Result.Failed)
    }
}
