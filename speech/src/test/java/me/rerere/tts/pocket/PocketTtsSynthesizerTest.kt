package me.rerere.tts.pocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PocketTtsSynthesizerTest {

    @Test
    fun stateSizeProductOfShapeDims() {
        assertEquals(32, PocketTtsSynthesizer.stateSize(longArrayOf(1L, 1L, 32L)))
        assertEquals(0, PocketTtsSynthesizer.stateSize(longArrayOf(0L, 0L)))
    }

    @Test
    fun stateSizeTreatsDynamicDimsAsZero() {
        assertEquals(0, PocketTtsSynthesizer.stateSize(longArrayOf(1L, -1L, 32L)))
        assertEquals(0, PocketTtsSynthesizer.stateSize(longArrayOf(1L, 0L, -1L)))
    }

    @Test
    fun stateSizeCoercesProductToNonNegative() {
        assertEquals(0, PocketTtsSynthesizer.stateSize(longArrayOf(0L, 1L)))
    }

    @Test
    fun mapsOutStateToStateInputName() {
        assertEquals("state_3", PocketTtsSynthesizer.stateInputName("out_state_3"))
        assertEquals("state_0", PocketTtsSynthesizer.stateInputName("out_state_0"))
    }

    @Test
    fun leavesNonStateOutputsUnmapped() {
        assertNull(PocketTtsSynthesizer.stateInputName("conditioning"))
        assertNull(PocketTtsSynthesizer.stateInputName("out_state_"))
        assertNull(PocketTtsSynthesizer.stateInputName("out_state_x"))
    }

    @Test
    fun eosStopDecisionAccountsForFramesAfterEos() {
        assertEquals(false, PocketTtsSynthesizer.shouldStopAfterEos(0, null, 0))
        assertEquals(false, PocketTtsSynthesizer.shouldStopAfterEos(1, 2, 0))
        assertEquals(true, PocketTtsSynthesizer.shouldStopAfterEos(2, 2, 0))
        assertEquals(true, PocketTtsSynthesizer.shouldStopAfterEos(3, 2, 0))
        assertEquals(true, PocketTtsSynthesizer.shouldStopAfterEos(5, 2, 2))
        assertEquals(false, PocketTtsSynthesizer.shouldStopAfterEos(3, 2, 2))
    }

    @Test
    fun flowBuffersPrecomputeStartEndPerStep() {
        val buffers = PocketTtsSynthesizer.flowBuffers(steps = 4)
        assertEquals(4, buffers.size)
        assertEquals(0.0f, buffers[0].first, 0.0f)
        assertEquals(0.25f, buffers[0].second, 0.0f)
        assertEquals(0.25f, buffers[1].first, 0.0f)
        assertEquals(0.5f, buffers[1].second, 0.0f)
        assertEquals(0.5f, buffers[2].first, 0.0f)
        assertEquals(0.75f, buffers[2].second, 0.0f)
        assertEquals(0.75f, buffers[3].first, 0.0f)
        assertEquals(1.0f, buffers[3].second, 1e-6f)
    }

    @Test
    fun chunkPlanSplitsFramesIntoBoundedRanges() {
        assertEquals(listOf<IntRange>(0..2, 3..5), PocketTtsSynthesizer.chunkPlan(6, 3))
        assertEquals(listOf<IntRange>(0..2, 3..5, 6..6), PocketTtsSynthesizer.chunkPlan(7, 3))
        assertEquals(emptyList<IntRange>(), PocketTtsSynthesizer.chunkPlan(0, 3))
        val chunked = PocketTtsSynthesizer.chunkPlan(1_000, 64)
        assertEquals(16, chunked.size)
        assertEquals(0..63, chunked.first())
        assertEquals(960..999, chunked.last())
    }
}
