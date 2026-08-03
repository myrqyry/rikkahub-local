package me.rerere.locallm

import org.junit.Assert.*
import org.junit.Test

class SdCatalogTest {

    @Test fun `every catalog entry builds an installable resolve URL`() {
        assertTrue(SdCatalog.ENTRIES.isNotEmpty())
        SdCatalog.ENTRIES.forEach { entry ->
            assertTrue(
                "resolveUrl must be an https huggingface URL: ${entry.resolveUrl()}",
                entry.resolveUrl().startsWith("https://huggingface.co/"),
            )
        }
    }

    @Test fun `every catalog entry carries a source URL users can open`() {
        SdCatalog.ENTRIES.forEach { entry ->
            assertTrue(
                "sourceUrl must be an https huggingface repo URL: ${entry.sourceUrl}",
                entry.sourceUrl.startsWith("https://huggingface.co/"),
            )
        }
    }

    @Test fun `findById returns the matching entry`() {
        val entry = SdCatalog.ENTRIES.first()
        assertSame(entry, SdCatalog.findById(entry.modelId))
        assertNull(SdCatalog.findById("nonexistent"))
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
