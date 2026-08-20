package me.rerere.rikkahub.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeMemoryProfileTest {

    private val gb = 1024L * 1024L * 1024L

    @Test
    fun `budget is net of android threshold`() {
        val budget = estimateRuntimeBudget(
            availMem = 6L * gb,
            thresholdBytes = 2L * gb,
        )
        assertEquals(4L * gb, budget)
    }

    @Test
    fun `budget never goes negative`() {
        val budget = estimateRuntimeBudget(
            availMem = gb,
            thresholdBytes = 2L * gb,
        )
        assertEquals(0L, budget)
    }

    @Test
    fun `workspace estimate scales with resolution`() {
        val small = workspaceEstimateBytes(width = 512, height = 512)
        val large = workspaceEstimateBytes(width = 1024, height = 1024)
        assertTrue("doubling linear dims should more than double the workspace estimate", large > small * 2)
    }

    @Test
    fun `model over budget is refused`() {
        val budget = estimateRuntimeBudget(availMem = 6L * gb, thresholdBytes = 2L * gb)
        assertNotNull(sdMemoryPolicyViolation(modelSizeBytes = 5L * gb, width = 512, height = 512, deviceRamBytes = budget))
    }

    @Test
    fun `model under budget is allowed`() {
        val budget = estimateRuntimeBudget(availMem = 6L * gb, thresholdBytes = 2L * gb)
        assertNull(sdMemoryPolicyViolation(modelSizeBytes = 2L * gb, width = 512, height = 512, deviceRamBytes = budget))
    }

    @Test
    fun `safety margin is included in required bytes`() {
        val profile = RuntimeMemoryProfile(
            modelResidentEstimate = gb,
            workspaceEstimate = 0L,
            outputEstimate = 0L,
            safetyMargin = SD_SAFETY_MARGIN_BYTES,
        )
        assertEquals(gb + SD_SAFETY_MARGIN_BYTES, profile.requiredBytes)
    }
}
