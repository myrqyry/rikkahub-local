package me.rerere.rikkahub.data.media

import me.rerere.rikkahub.data.ai.tools.image.ImageToolCatalog
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMediaStoreTest {

    @Test
    fun `entity maps relative path, model, prompt, type and source lineage`() {
        val entity = DefaultImageMediaStore.buildEntity(
            relativePath = "images/1_2_0.png",
            modelName = "imagen",
            prompt = "a cat",
            timestamp = 1L,
            type = GenMediaEntity.TYPE_IMAGE_EDIT,
            sourceArtifacts = listOf("img_3"),
        )
        assertEquals("images/1_2_0.png", entity.path)
        assertEquals("imagen", entity.modelId)
        assertEquals("a cat", entity.prompt)
        assertEquals(1L, entity.createAt)
        assertEquals(GenMediaEntity.TYPE_IMAGE_EDIT, entity.type)
        assertEquals("img_3", entity.sourcePaths)
    }

    @Test
    fun `generation type maps to image_generation`() {
        val entity = DefaultImageMediaStore.buildEntity(
            relativePath = "images/1.png",
            modelName = "m",
            prompt = "p",
            timestamp = 1L,
            type = GenMediaEntity.TYPE_IMAGE_GENERATION,
            sourceArtifacts = emptyList(),
        )
        assertEquals(GenMediaEntity.TYPE_IMAGE_GENERATION, entity.type)
    }

    @Test
    fun `artifact id is stable per gallery id`() {
        assertEquals("img_12", ImageToolCatalog.artifactIdFor(12))
    }

    @Test
    fun `filename sanitizes provider controlled display name`() {
        val path = buildImageRelativePath(123L, "flux/sd:1")
        assertTrue("expected prefix, got: $path", path.startsWith("images/123_flux_sd_1_"))
        assertTrue("expected png suffix, got: $path", path.endsWith(".png"))
        val name = path.removePrefix("images/")
        assertFalse("unsanitized slash in: $name", name.contains('/'))
        assertFalse("unsanitized colon in: $name", name.contains(':'))
    }

    @Test
    fun `blank display name falls back to image`() {
        val path = buildImageRelativePath(1L, "")
        assertTrue("expected image fallback, got: $path", path.startsWith("images/1_image_"))
        assertTrue("expected png suffix, got: $path", path.endsWith(".png"))
    }

    @Test
    fun `same millisecond saves never produce identical filenames`() {
        val a = buildImageRelativePath(1L, "imagen")
        val b = buildImageRelativePath(1L, "imagen")
        assertTrue("expected prefix, got: $a", a.startsWith("images/1_imagen_"))
        assertTrue("expected prefix, got: $b", b.startsWith("images/1_imagen_"))
        assertTrue("collision: $a vs $b", a != b)
    }
}
