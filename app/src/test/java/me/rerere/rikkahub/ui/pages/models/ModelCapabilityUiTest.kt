package me.rerere.rikkahub.ui.pages.models

import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.capability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCapabilityUiTest {
    @Test
    fun everyCapabilityHasLabelAndIcon() {
        ModelCapability.entries.forEach { capability ->
            assertTrue("labelRes for $capability", capability.labelRes != 0)
            assertNotNull("icon for $capability", capability.icon)
        }
    }

    @Test
    fun everyLifecycleHasLabel() {
        ModelLifecycle.entries.forEach { lifecycle ->
            assertTrue("labelRes for $lifecycle", lifecycle.labelRes != 0)
        }
    }

    @Test
    fun everyRoleMapsToDistinctCapability() {
        val mapped = ModelRole.entries.map { it to it.capability() }
        val distinct = mapped.map { it.second }.toSet()
        assertEquals("distinct capability per role", mapped.size, distinct.size)
        assertFalse("no role maps to an uncategorized capability", distinct.contains(ModelCapability.RERANKING))
    }
}
