package me.rerere.rikkahub.ui.pages.models

import me.rerere.locallm.LocalRuntime
import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelMemoryFitTest {
    @Test
    fun `eligible model that fits is FitsNow`() {
        val model = localModel(sizeBytes = "700", runtime = LocalRuntime.LiteRT)

        assertEquals(ModelMemoryFit.FitsNow(700, 1000), model.memoryFit(1000))
    }

    @Test
    fun `eligible model that does not fit carries admission numbers`() {
        val model = localModel(sizeBytes = "800", runtime = LocalRuntime.LiteRT)

        assertEquals(
            ModelMemoryFit.NeedsMoreMemory(800, 1000, 1143),
            model.memoryFit(1000),
        )
    }

    @Test
    fun `missing snapshot is Checking for eligible model`() {
        assertEquals(
            ModelMemoryFit.Checking,
            localModel(sizeBytes = "700", runtime = LocalRuntime.LiteRT).memoryFit(null),
        )
    }

    @Test
    fun `ineligible and malformed models are Unavailable`() {
        assertEquals(ModelMemoryFit.Unavailable, localModel(sizeBytes = null).memoryFit(1000))
        assertEquals(ModelMemoryFit.Unavailable, localModel(sizeBytes = "0").memoryFit(1000))
        assertEquals(ModelMemoryFit.Unavailable, localModel(sizeBytes = "-1").memoryFit(1000))
        assertEquals(ModelMemoryFit.Unavailable, localModel(sizeBytes = "not-a-number").memoryFit(1000))
        assertEquals(
            ModelMemoryFit.Unavailable,
            localModel(runtime = LocalRuntime.StableDiffusion).memoryFit(1000),
        )
        assertEquals(
            ModelMemoryFit.Unavailable,
            localModel(lifecycle = ModelLifecycle.INSTALLED).memoryFit(1000),
        )
        assertEquals(ModelMemoryFit.Unavailable, localModel(installed = false).memoryFit(1000))
    }

    private fun localModel(
        sizeBytes: String? = "700",
        runtime: LocalRuntime = LocalRuntime.LiteRT,
        lifecycle: ModelLifecycle = ModelLifecycle.READY,
        installed: Boolean = true,
    ) = ModelDescriptor(
        id = "local-model",
        displayName = "Local model",
        source = ModelSource.Local(runtime),
        capabilities = setOf(ModelCapability.CHAT),
        lifecycle = lifecycle,
        installed = installed,
        metadata = sizeBytes?.let { mapOf("sizeBytes" to it) }.orEmpty(),
    )
}
