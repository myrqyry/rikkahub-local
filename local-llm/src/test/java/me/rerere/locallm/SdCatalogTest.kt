package me.rerere.locallm

import org.junit.Assert.*
import org.junit.Test

class SdCatalogTest {

    @Test fun `empty catalog returns empty list`() {
        assertTrue(SdCatalog.ENTRIES.isEmpty())
    }

    @Test fun `findById on empty catalog returns null`() {
        assertNull(SdCatalog.findById("nonexistent"))
    }

    @Test fun `findByFamily on empty catalog returns empty`() {
        assertTrue(SdCatalog.findByFamily("sdxl").isEmpty())
    }

    @Test fun `catalog entry resolveUrl builds correct URL`() {
        val entry = SdCatalogEntry(
            displayName = "Test",
            family = "test",
            format = "gguf",
            description = "",
            modelId = "test/model",
            modelFile = "test.gguf",
            sizeBytes = 1000,
            license = "MIT",
            minDeviceMemoryGb = 8,
        )
        assertEquals("https://huggingface.co/test/model/resolve/main/test.gguf", entry.resolveUrl())
    }
}
