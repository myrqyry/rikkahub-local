package me.rerere.locallm

import kotlin.io.path.createTempFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.ai.provider.LITERT_PROVIDER_ID
import me.rerere.ai.provider.STABLE_DIFFUSION_PROVIDER_ID
import me.rerere.locallm.litert.image.FLUX2_KLEIN_MODEL

class ModelCatalogTest {
    @Test
    fun `flux is an image model using the litert package installer`() {
        val entry = ModelCatalog.entries.single { it.model.id == FLUX2_KLEIN_MODEL.id }

        assertEquals(ModelModality.IMAGE, entry.modality)
        assertEquals(LocalRuntime.LiteRT, entry.runtime)
        assertEquals(LITERT_PROVIDER_ID, entry.providerId)
        assertEquals(ModelInstallKind.FLUX2_KLEIN_PACKAGE, entry.installKind)
    }

    @Test
    fun `routing rejects an incompatible provider`() {
        val entry = ModelCatalog.entries.single()

        val rejected = runCatching { ModelRouting.resolve(entry, STABLE_DIFFUSION_PROVIDER_ID) }
        assertTrue(rejected.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `unknown gguf classification uses the stable diffusion route`() {
        val file = createTempFile(suffix = ".gguf").toFile()
        file.writeBytes("GGUF".toByteArray())

        val route = ModelRouting.classifyGguf(file)

        assertEquals(LocalRuntime.StableDiffusion, route.runtime)
        assertEquals(STABLE_DIFFUSION_PROVIDER_ID, route.providerId)
    }

    @Test
    fun `image catalog is grouped independently from runtime`() {
        assertEquals(
            listOf(FLUX2_KLEIN_MODEL.modelId),
            ModelCatalog.byModality(ModelModality.IMAGE).map { it.model.modelId },
        )
    }
}
