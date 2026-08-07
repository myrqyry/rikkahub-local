package me.rerere.locallm.litert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionGemmaMobileActionsProfileTest {

    @Test
    fun `recognizes both published Mobile Actions filenames`() {
        assertTrue(FunctionGemmaMobileActionsProfile.isKnownModelFile(
            "mobile_actions_q8_ekv1024.litertlm",
        ))
        assertTrue(FunctionGemmaMobileActionsProfile.isKnownModelFile(
            "functiongemma-270m-ft-mobile-actions_Google_Tensor_G5.litertlm",
        ))
    }

    @Test
    fun `does not classify unrelated LiteRT files as FunctionGemma`() {
        assertFalse(FunctionGemmaMobileActionsProfile.isKnownModelFile(
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        ))
    }

    @Test
    fun `generic Mobile Actions artifact gets verified 1024 context defaults`() {
        val config = LiteRtModelDefaults.forModelFile(
            FunctionGemmaMobileActionsProfile.GENERIC_MODEL_FILE,
        )

        assertEquals(FunctionGemmaMobileActionsProfile.GENERIC_MODEL_FILE, config.modelFile)
        assertEquals(1024, config.maxTokens)
        assertEquals(1024, config.maxContextLength)
        assertEquals(listOf("cpu", "gpu"), config.preferredAccelerators)
        assertEquals(288_964_608L, config.sizeBytes)
        assertFalse(config.supportsImage)
        assertFalse(config.supportsAudio)
        assertFalse(config.supportsThinking)
    }

    @Test
    fun `Tensor G5 artifact is recognized but does not receive generic backend defaults`() {
        assertNull(FunctionGemmaMobileActionsProfile.runtimeConfigFor(
            FunctionGemmaMobileActionsProfile.GOOGLE_TENSOR_G5_MODEL_FILE,
        ))

        val fallback = LiteRtModelDefaults.forModelFile(
            FunctionGemmaMobileActionsProfile.GOOGLE_TENSOR_G5_MODEL_FILE,
        )
        assertEquals("<unknown>", fallback.modelFile)
    }
}
