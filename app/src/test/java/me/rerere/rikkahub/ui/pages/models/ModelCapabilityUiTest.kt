package me.rerere.rikkahub.ui.pages.models

import me.rerere.locallm.LocalRuntime
import me.rerere.rikkahub.data.modelregistry.ModelCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.data.modelregistry.capability

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

    @Test
    fun `disabled provider takes precedence`() {
        val model = descriptor(
            source = ModelSource.Cloud("openai", "gpt-image-1"),
            providerEnabled = false,
            connected = false,
        )

        assertEquals(ModelInventoryStatus.PROVIDER_DISABLED, model.inventoryStatus())
    }

    @Test
    fun `disconnected cloud model reports unavailable connection`() {
        val model = descriptor(
            source = ModelSource.Cloud("openai", "gpt-image-1"),
            connected = false,
        )

        assertEquals(ModelInventoryStatus.CONNECTION_UNAVAILABLE, model.inventoryStatus())
    }

    @Test
    fun `non-ready local model reports lifecycle status`() {
        val model = descriptor(
            source = ModelSource.Local(LocalRuntime.StableDiffusion),
            lifecycle = ModelLifecycle.INSTALLED,
        )

        assertEquals(ModelInventoryStatus.NOT_READY, model.inventoryStatus())
    }

    @Test
    fun `ready connected model has no inventory status`() {
        val model = descriptor(
            source = ModelSource.Cloud("openai", "gpt-image-1"),
            connected = true,
        )

        assertNull(model.inventoryStatus())
    }

    private fun descriptor(
        source: ModelSource = ModelSource.Cloud("openai", "gpt-image-1"),
        providerEnabled: Boolean = true,
        connected: Boolean = true,
        lifecycle: ModelLifecycle = ModelLifecycle.READY,
    ) = ModelDescriptor(
        id = "test-model",
        displayName = "Test model",
        source = source,
        capabilities = emptySet(),
        providerEnabled = providerEnabled,
        connected = connected,
        lifecycle = lifecycle,
    )
}
