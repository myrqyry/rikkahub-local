package me.rerere.rikkahub.data.rag

import java.io.File
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.*
import org.junit.Test

class EmbeddingGemma2VariantsTest {
    private val folder = File("/not-installed-qwen")

    @Test fun variantsArePinnedSeparatelyWithExactSizesAndHashes() {
        val text = EmbeddingGemma2Variant.TEXT
        val vision = EmbeddingGemma2Variant.VISION
        val full = EmbeddingGemma2Variant.FULL
        assertEquals(EmbeddingGemma2Model.MODEL_ID, text.modelId)
        assertEquals(EmbeddingGemma2Model.SPACE_ID, text.spaceId)
        assertEquals(164626432L, text.sizeBytes)
        assertEquals(387710976L, vision.sizeBytes)
        assertEquals("92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0", vision.sha256)
        assertEquals(484622336L, full.sizeBytes)
        assertEquals("e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c", full.sha256)
        assertEquals(3, EmbeddingGemma2Variant.entries.map { it.modelId }.distinct().size)
        assertEquals(3, EmbeddingGemma2Variant.entries.map { it.spaceId }.distinct().size)
        assertTrue(vision.downloadUrl.contains("/e301f74d5551b0c2641bd5cb4652a76239d5c5f8/"))
        assertTrue(full.downloadUrl.contains("/24d962e906c7d332c6428e71c9676855024569e2/"))
    }

    @Test fun variantsAdvertiseOnlyModalitiesTheirBundlesContain() {
        assertFalse(EmbeddingGemma2Variant.TEXT.supportsImage)
        assertFalse(EmbeddingGemma2Variant.TEXT.supportsAudio)
        assertTrue(EmbeddingGemma2Variant.VISION.supportsImage)
        assertFalse(EmbeddingGemma2Variant.VISION.supportsAudio)
        assertTrue(EmbeddingGemma2Variant.FULL.supportsImage)
        assertTrue(EmbeddingGemma2Variant.FULL.supportsAudio)
    }

    @Test fun selectingMultimodalVariantUsesItForRagInsteadOfTextOnlyOrQwen() {
        for (variant in EmbeddingGemma2Variant.entries) {
            val selected = Settings().copy(ragEmbeddingModel = variant.modelId)
            assertEquals(
                RagEmbeddingSource.LocalEmbeddingGemma2(variant),
                resolveRagEmbeddingSource(selected, folder, localReady = true)
            )
        }
    }

    @Test fun unknownOrMissingVariantDoesNotSilentlyFallbackToAnotherLocalModel() {
        val settings = Settings().copy(ragEmbeddingModel = "local:embeddinggemma-2-invalid")
        assertNull(resolveRagEmbeddingSource(settings, folder, localReady = true))
    }

    @Test fun differentBundlesHaveDifferentIndexSpacesEvenWithSameOutputDimensions() {
        val variants = EmbeddingGemma2Variant.entries
        assertTrue(variants.all { it.dimension == 768 })
        assertNotEquals(variants[0].spaceId, variants[1].spaceId)
        assertNotEquals(variants[1].spaceId, variants[2].spaceId)
    }

    @Test fun mediaFileValidationRejectsCorruptedOrWrongModelWithoutTouchingIt() {
        val file = File.createTempFile("embeddinggemma2-test-", ".litertlm")
        try {
            file.writeText("not a real model")
            assertFalse(EmbeddingGemma2Variant.VISION.verify(file))
            assertFalse(EmbeddingGemma2Variant.FULL.verify(file))
            assertTrue(file.exists())
        } finally {
            file.delete()
        }
    }
}
