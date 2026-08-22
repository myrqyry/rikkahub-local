package me.rerere.rikkahub.subagent

import kotlin.uuid.Uuid
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubAgentProfileResolverTest {

    private fun profile(
        name: String,
        systemPrompt: String = "",
        modelId: Uuid? = null,
        enabled: Boolean = true,
    ) = SubAgentProfile(
        name = name,
        systemPrompt = systemPrompt,
        modelId = modelId,
        enabled = enabled,
    )

    // --- resolve ---

    @Test
    fun `null agent returns NotRequested`() {
        val result = SubAgentProfileResolver.resolve(null, emptyList())
        assertTrue(result is SubAgentProfileResolver.Result.NotRequested)
    }

    @Test
    fun `blank agent returns NotRequested`() {
        val result = SubAgentProfileResolver.resolve("  ", emptyList())
        assertTrue(result is SubAgentProfileResolver.Result.NotRequested)
    }

    @Test
    fun `match enabled profile case-insensitive`() {
        val p = profile("Researcher", systemPrompt = "You are a researcher.")
        val result = SubAgentProfileResolver.resolve("researcher", listOf(p))
        assertTrue(result is SubAgentProfileResolver.Result.Resolved)
        assertEquals("Researcher", (result as SubAgentProfileResolver.Result.Resolved).profile.name)
    }

    @Test
    fun `disabled profile is ignored`() {
        val p = profile("Agent", enabled = false)
        val result = SubAgentProfileResolver.resolve("Agent", listOf(p))
        assertTrue(result is SubAgentProfileResolver.Result.Failed)
    }

    @Test
    fun `no matching profile with empty list returns Failed`() {
        val result = SubAgentProfileResolver.resolve("Nonexistent", emptyList())
        assertTrue(result is SubAgentProfileResolver.Result.Failed)
    }

    @Test
    fun `no matching profile with nonempty list returns Failed listing available names`() {
        val p1 = profile("Researcher")
        val p2 = profile("Writer")
        val result = SubAgentProfileResolver.resolve("Nonexistent", listOf(p1, p2))
        assertTrue(result is SubAgentProfileResolver.Result.Failed)
        val msg = (result as SubAgentProfileResolver.Result.Failed).message
        assertTrue(msg.contains("Researcher"))
        assertTrue(msg.contains("Writer"))
    }

    @Test
    fun `duplicate enabled profiles with same name returns Failed`() {
        val p1 = profile("Researcher")
        val p2 = profile("Researcher")
        val result = SubAgentProfileResolver.resolve("Researcher", listOf(p1, p2))
        assertTrue(result is SubAgentProfileResolver.Result.Failed)
        assertTrue((result as SubAgentProfileResolver.Result.Failed).message.contains("duplicate"))
    }

    // --- combinedModelResolution ---

    @Test
    fun `combined Inherit plus profile modelId returns Resolved with profile modelId`() {
        val modelUuid = Uuid.random()
        val p = profile("Agent", modelId = modelUuid)
        val result = SubAgentProfileResolver.combinedModelResolution(
            SubAgentModelResolver.Result.Inherit,
            p,
        )
        assertTrue(result is SubAgentModelResolver.Result.Resolved)
        assertEquals(modelUuid.toString(), (result as SubAgentModelResolver.Result.Resolved).modelId)
    }

    @Test
    fun `combined Inherit plus no profile modelId returns Inherit`() {
        val p = profile("Agent")
        val result = SubAgentProfileResolver.combinedModelResolution(
            SubAgentModelResolver.Result.Inherit,
            p,
        )
        assertTrue(result is SubAgentModelResolver.Result.Inherit)
    }

    @Test
    fun `combined Resolved passthrough ignores profile modelId`() {
        val modelUuid = Uuid.random()
        val p = profile("Agent", modelId = Uuid.random())
        val result = SubAgentProfileResolver.combinedModelResolution(
            SubAgentModelResolver.Result.Resolved("explicit-model"),
            p,
        )
        assertTrue(result is SubAgentModelResolver.Result.Resolved)
        assertEquals("explicit-model", (result as SubAgentModelResolver.Result.Resolved).modelId)
    }

    @Test
    fun `combined Failed passthrough ignores profile modelId`() {
        val p = profile("Agent", modelId = Uuid.random())
        val result = SubAgentProfileResolver.combinedModelResolution(
            SubAgentModelResolver.Result.Failed("bad model"),
            p,
        )
        assertTrue(result is SubAgentModelResolver.Result.Failed)
        assertEquals("bad model", (result as SubAgentModelResolver.Result.Failed).message)
    }

    // --- serialization round-trip ---

    @Test
    fun `SubAgentProfile serializes and deserializes`() {
        val modelUuid = Uuid.random()
        val p = profile("Coder", systemPrompt = "Write code.", modelId = modelUuid, enabled = true)
        val json = Json.encodeToString(p)
        val decoded = Json.decodeFromString<SubAgentProfile>(json)
        assertEquals(p.name, decoded.name)
        assertEquals(p.systemPrompt, decoded.systemPrompt)
        assertEquals(p.modelId, decoded.modelId)
        assertEquals(p.enabled, decoded.enabled)
    }

    @Test
    fun `disabled profile with modelId does not interfere with combined resolution`() {
        val modelUuid = Uuid.random()
        val disabled = profile("Disabled", modelId = modelUuid, enabled = false)
        val result = SubAgentProfileResolver.resolve("Disabled", listOf(disabled))
        assertTrue(result is SubAgentProfileResolver.Result.Failed)
    }
}
