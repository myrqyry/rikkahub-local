package me.rerere.rikkahub.ui.pages.models.components

import me.rerere.locallm.LocalRuntime
import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelSource
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceBadgeTest {
    private fun model(source: ModelSource, metadata: Map<String, String> = emptyMap()) =
        ModelDescriptor(
            id = "m",
            displayName = "M",
            source = source,
            capabilities = setOf(ModelCapability.CHAT),
            enabledCapabilities = setOf(ModelCapability.CHAT),
            lifecycle = ModelLifecycle.READY,
            metadata = metadata,
        )

    @Test
    fun `local source label is On device`() {
        assertEquals("On device", sourceDisplayName(model(ModelSource.Local(LocalRuntime.LiteRT))))
    }

    @Test
    fun `cloud source uses provider metadata name`() {
        assertEquals(
            "OpenAI",
            sourceDisplayName(
                model(
                    ModelSource.Cloud("uuid", "gpt"),
                    metadata = mapOf("provider" to "OpenAI"),
                ),
            ),
        )
    }

    @Test
    fun `cloud source falls back to provider id`() {
        assertEquals("uuid", sourceDisplayName(model(ModelSource.Cloud("uuid", "gpt"))))
    }
}
