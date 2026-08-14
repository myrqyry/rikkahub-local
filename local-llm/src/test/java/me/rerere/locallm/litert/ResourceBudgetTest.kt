package me.rerere.locallm.litert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceBudgetTest {

    @Test
    fun `default budget is unbounded`() {
        assertTrue(ResourceBudget().isUnbounded())
    }

    @Test
    fun `configured budget is not unbounded`() {
        assertFalse(ResourceBudget(maxDurationMs = 1000).isUnbounded())
    }

    @Test
    fun `empty usage respects any budget`() {
        val budget = ResourceBudget(
            maxDurationMs = 1000,
            maxNetworkRequests = 5,
            maxDownloadBytes = 1024,
            maxWriteBytes = 1024,
            maxOutputBytes = 1024,
            maxToolCalls = 3,
            maxConcurrency = 2,
        )
        assertTrue(budget.respects(ResourceUsage()))
    }

    @Test
    fun `within limits respects`() {
        val budget = ResourceBudget(maxDurationMs = 1000, maxToolCalls = 3)
        assertTrue(budget.respects(ResourceUsage(elapsedMs = 999, toolCalls = 3)))
    }

    @Test
    fun `duration over limit fails`() {
        assertFalse(ResourceBudget(maxDurationMs = 1000).respects(ResourceUsage(elapsedMs = 1001)))
    }

    @Test
    fun `download bytes over limit fails`() {
        assertFalse(ResourceBudget(maxDownloadBytes = 1000).respects(ResourceUsage(downloadBytes = 1001)))
    }

    @Test
    fun `null usage fields are ignored`() {
        // budget caps download but usage reports only elapsedMs → passes
        assertTrue(ResourceBudget(maxDownloadBytes = 1000).respects(ResourceUsage(elapsedMs = 5000)))
    }

    @Test
    fun `compute budget respected`() {
        val budget = ResourceBudget(compute = ComputeBudget(maxCost = 0.5))
        assertTrue(budget.respects(ResourceUsage(compute = ComputeUsage(cost = 0.49))))
        assertFalse(budget.respects(ResourceUsage(compute = ComputeUsage(cost = 0.51))))
    }

    @Test
    fun `nested compute budget with null usage passes`() {
        val budget = ResourceBudget(compute = ComputeBudget(maxAcceleratorMemoryBytes = 1000))
        assertTrue(budget.respects(ResourceUsage()))
    }
}
