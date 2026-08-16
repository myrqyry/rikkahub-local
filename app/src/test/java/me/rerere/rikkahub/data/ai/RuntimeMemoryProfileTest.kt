package me.rerere.rikkahub.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeMemoryProfileTest {

    private val gb = 1024L * 1024L * 1024L

    @Test
    fun `budget is net of reserves`() {
        val budget = estimateRuntimeBudget(
            availMem = 6L * gb,
            androidReserve = (3L * gb) / 2,
            knownWorkingSet = gb / 2,
        )
        assertEquals(4L * gb, budget)
    }

    @Test
    fun `budget never goes negative`() {
        val budget = estimateRuntimeBudget(
            availMem = gb,
            androidReserve = 2L * gb,
            knownWorkingSet = gb,
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
        val budget = estimateRuntimeBudget(availMem = 6L * gb, androidReserve = (3L * gb) / 2, knownWorkingSet = gb / 2)
        assertNotNull(sdMemoryPolicyViolation(modelSizeBytes = 5L * gb, width = 512, height = 512, deviceRamBytes = budget))
    }

    @Test
    fun `model under budget is allowed`() {
        val budget = estimateRuntimeBudget(availMem = 6L * gb, androidReserve = (3L * gb) / 2, knownWorkingSet = gb / 2)
        assertNull(sdMemoryPolicyViolation(modelSizeBytes = 2L * gb, width = 512, height = 512, deviceRamBytes = budget))
    }
}
