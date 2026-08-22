package me.rerere.rikkahub.ui.pages.models

import me.rerere.rikkahub.data.modelregistry.ModelAssignments
import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.locallm.LocalRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAssignmentsSummaryTest {
    private fun local(id: String, name: String) = ModelDescriptor(
        id = id,
        displayName = name,
        source = ModelSource.Local(LocalRuntime.LiteRT),
        capabilities = setOf(ModelCapability.CHAT),
        enabledCapabilities = setOf(ModelCapability.CHAT),
        lifecycle = ModelLifecycle.READY,
    )

    @Test
    fun `returns one row per assigned role with resolved model`() {
        val gemma = local("gemma", "Gemma 4")
        val assignments = ModelAssignments(
            defaults = mapOf(
                ModelRole.CHAT to "gemma",
                ModelRole.VISION to "missing-model",
            ),
        )
        val rows = defaultAssignmentsSummary(assignments, listOf(gemma))
        assertEquals(1, rows.size)
        assertEquals(ModelRole.CHAT, rows[0].role)
        assertEquals(gemma, rows[0].model)
    }

    @Test
    fun `skips rows whose model cannot be resolved`() {
        val assignments = ModelAssignments(
            defaults = mapOf(ModelRole.OCR to "ghost"),
        )
        assertTrue(defaultAssignmentsSummary(assignments, emptyList()).isEmpty())
    }

    @Test
    fun `no assignment defaults produce empty list`() {
        assertTrue(defaultAssignmentsSummary(ModelAssignments(), emptyList()).isEmpty())
    }
}
