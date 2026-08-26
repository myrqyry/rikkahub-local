package me.rerere.rikkahub.ui.pages.models.components

import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.pages.models.ModelMemoryFit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelMemoryFitBadgeTest {
    @Test
    fun `fit states map to labels`() {
        assertEquals(R.string.models_memory_fit_now, ModelMemoryFit.FitsNow(700, 1000).labelRes())
        assertEquals(R.string.models_memory_checking, ModelMemoryFit.Checking.labelRes())
        assertEquals(R.string.models_memory_unavailable, ModelMemoryFit.Unavailable.labelRes())
        assertEquals(
            R.string.models_memory_needs_more,
            ModelMemoryFit.NeedsMoreMemory(800, 1000, 1143).labelRes(),
        )
    }

    @Test
    fun `needs more memory label receives required and available megabytes`() {
        assertArrayEquals(
            arrayOf<Any>(1143L, 1000L),
            ModelMemoryFit.NeedsMoreMemory(800, 1_000_000_000, 1_143_000_000).labelArgs(),
        )
    }
}
