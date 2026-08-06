package me.rerere.rikkahub.data.media

import me.rerere.rikkahub.data.ai.tools.image.ImageToolCatalog
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import org.junit.Assert.assertEquals
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
}
