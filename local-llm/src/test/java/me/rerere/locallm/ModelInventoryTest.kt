package me.rerere.locallm

import org.junit.Assert.*
import org.junit.Test

class ModelInventoryTest {

    @Test fun `addModelEntry stores and retrieves by id`() {
        val inv = ModelInventory()
        val entry = ModelEntry(
            id = "test-1",
            displayName = "SDXL Turbo",
            runtime = LocalRuntime.StableDiffusion,
            family = "sdxl-turbo",
            format = "gguf",
            source = ModelSource.Catalog("sdxl-turbo-v1"),
            sourceUrl = "https://huggingface.co/test/sdxl-turbo/resolve/main/model.gguf",
            filePath = "/data/rikka/local-models/stable-diffusion/model.gguf",
            sizeBytes = 4_000_000_000L,
            license = "MIT",
            validated = true,
            addedAt = 1000L,
        )
        inv.add(entry)
        assertEquals(entry, inv.getById("test-1"))
        assertEquals(1, inv.list().size)
    }

    @Test fun `removeModelEntry deletes entry`() {
        val inv = ModelInventory()
        inv.add(ModelEntry(id = "m1", displayName = "M1", runtime = LocalRuntime.StableDiffusion, format = "gguf", source = ModelSource.LocalImport, filePath = "/p", sizeBytes = 1, validated = true, addedAt = 1))
        inv.add(ModelEntry(id = "m2", displayName = "M2", runtime = LocalRuntime.StableDiffusion, format = "gguf", source = ModelSource.LocalImport, filePath = "/p", sizeBytes = 1, validated = true, addedAt = 2))
        inv.remove("m1")
        assertNull(inv.getById("m1"))
        assertNotNull(inv.getById("m2"))
    }

    @Test fun `listByRuntime filters correctly`() {
        val inv = ModelInventory()
        inv.add(ModelEntry(id = "sd1", displayName = "SD1", runtime = LocalRuntime.StableDiffusion, format = "gguf", source = ModelSource.LocalImport, filePath = "/p", sizeBytes = 1, validated = true, addedAt = 1))
        inv.add(ModelEntry(id = "lt1", displayName = "LT1", runtime = LocalRuntime.LiteRT, format = "litertlm", source = ModelSource.LocalImport, filePath = "/p", sizeBytes = 1, validated = true, addedAt = 2))
        assertEquals(1, inv.listByRuntime(LocalRuntime.StableDiffusion).size)
        assertEquals(1, inv.listByRuntime(LocalRuntime.LiteRT).size)
    }

    @Test fun `modelSourceCatalog round-trips serialization`() {
        val src = ModelSource.Catalog("sdxl-turbo-v1")
        assertTrue(src is ModelSource.Catalog)
        assertEquals("sdxl-turbo-v1", (src as ModelSource.Catalog).entryId)
    }
}
