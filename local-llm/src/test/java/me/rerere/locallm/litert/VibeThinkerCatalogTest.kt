package me.rerere.locallm.litert

import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Modality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VibeThinkerCatalogTest {
    private val candidate = LiteRtCatalog.ENTRIES.single {
        it.modelId == "litert-community/VibeThinker-3B"
    }

    @Test fun candidateUsesImmutableRemoteRevisionAndUniqueLocalFilename() {
        assertEquals("model.litertlm", candidate.sourceFile)
        assertEquals("VibeThinker-3B-int4.litertlm", candidate.modelFile)
        assertEquals(2059236272L, candidate.sizeBytes)
        assertEquals(
            "https://huggingface.co/litert-community/VibeThinker-3B/resolve/" +
                "7790b109a7861b2f6982ccc9877d27faba57a712/model.litertlm",
            candidate.resolveUrl(),
        )
        assertEquals(
            "7885496ccd8f70680474e66e88eccfdc966c1870a39b5fdbea837f897e4742a0",
            candidate.sha256,
        )
        assertEquals(LiteRtCatalog.ENTRIES.size, LiteRtCatalog.ENTRIES.map { it.modelFile }.toSet().size)
    }

    @Test fun reasoningCandidateDoesNotClaimToolsOrBecomeGeneralAgentDefault() {
        val config = candidate.config()
        val caps = LiteRtModelMetadata.deriveCapabilities(candidate.modelFile)
        assertFalse(candidate.recommended)
        assertFalse(config.supportsToolCalling)
        assertTrue(config.supportsThinking)
        assertEquals(listOf(Modality.TEXT), caps.inputModalities)
        assertEquals(listOf(ModelAbility.REASONING), caps.abilities)
        assertEquals(4096, config.maxContextLength)
        assertTrue(config.maxTokens >= 2048)
        assertTrue(config.maxTokens <= config.maxContextLength!!)
    }

    @Test fun existingGeneralChatModelsRetainToolSupport() {
        val name = "LFM2.5-1.2B-Instruct_int4.litertlm"
        assertTrue(LiteRtModelMetadata.deriveCapabilities(name).abilities.contains(ModelAbility.TOOL))
    }
}
