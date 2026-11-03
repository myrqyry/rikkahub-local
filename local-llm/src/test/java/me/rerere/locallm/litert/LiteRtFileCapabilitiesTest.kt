package me.rerere.locallm.litert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtFileCapabilitiesTest {
    private fun llm(vision: Boolean, audio: Boolean, max: Int? = null) =
        LiteRtFileCapabilities(
            kind = LiteRtFileCapabilities.Kind.LLM,
            text = true,
            vision = vision,
            audio = audio,
            video = false,
            maxContextTokens = max,
        )

    @Test fun bundleMayDenyCatalogVisionWithoutDenyingTextChat() {
        val p = llm(vision = false, audio = false).executionPlan(true, true, 4096)
        assertFalse(p.image)
        assertFalse(p.audio)
        assertEquals(4096, p.maxNumTokens)
    }

    @Test fun bundleEnablesOnlyRequestedModalities() {
        val p = llm(vision = true, audio = true).executionPlan(false, true, 2048)
        assertFalse(p.image)
        assertTrue(p.audio)
    }

    @Test fun modelContextCeilingClampsOnlyWhenPositiveAndKnown() {
        assertEquals(512, llm(true, false, 512).executionPlan(true, false, 4096).maxNumTokens)
        assertEquals(128, llm(true, false, 512).executionPlan(true, false, 128).maxNumTokens)
        assertEquals(4096, llm(true, false).executionPlan(true, false, 4096).maxNumTokens)
    }

    @Test fun embeddingModelCannotBeAccidentallyLoadedAsChat() {
        val embed = LiteRtFileCapabilities(
            kind = LiteRtFileCapabilities.Kind.EMBEDDING,
            text = true, vision = false, audio = false, video = false,
        )
        val err = runCatching { embed.executionPlan(false, false, 4096) }.exceptionOrNull()
        assertTrue(err is IllegalArgumentException)
        assertTrue(err!!.message!!.contains("embedding model"))
    }

    @Test fun unrecognizedBundleMustNotDisableCatalogCapabilities() {
        val unknown = LiteRtFileCapabilities(
            kind = LiteRtFileCapabilities.Kind.UNKNOWN,
            text = false, vision = false, audio = false, video = false,
        )
        val p = unknown.executionPlan(true, true, 2048)
        assertTrue(p.image)
        assertTrue(p.audio)
    }

    @Test fun unsupportedMetadataIsNotConflatedWithNegativeCapabilities() {
        val absent: LiteRtFileCapabilities? = null
        val cap = absent?.executionPlan(true, true, 4096)
            ?: LiteRtFileCapabilities.ExecutionPlan(image = true, audio = true, maxNumTokens = 4096)
        assertTrue(cap.image)
        assertTrue(cap.audio)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonpositiveRequestedTokenLimitIsRejected() {
        llm(true, true).executionPlan(true, true, 0)
    }
}
