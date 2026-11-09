package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GenerationHandlerCancellationTest {
    @Test
    fun `cancellation during tool execution must propagate instead of becoming tool failed`() {
        val cancellation = CancellationException("user stopped generation")
        val caught = runCatching { throw cancellation }.exceptionOrNull()!!
        val propagated = assertThrows(CancellationException::class.java) {
            propagateToolCancellation(caught)
        }
        assertEquals(cancellation, propagated)
    }

    @Test
    fun `ordinary tool failure should keep its normal error envelope path`() {
        propagateToolCancellation(IllegalStateException("tool unavailable"))
    }
}
