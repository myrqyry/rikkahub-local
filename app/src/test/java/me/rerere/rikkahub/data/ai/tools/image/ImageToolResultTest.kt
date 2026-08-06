package me.rerere.rikkahub.data.ai.tools.image

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageToolResultTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `artifact round-trips through json preserving all fields`() {
        val artifact = StoredImageArtifact(
            artifactId = "img_7",
            path = "/data/user/0/me.rerere.rikkahub/files/images/1_2_0.png",
            uri = "file:///data/user/0/me.rerere.rikkahub/files/images/1_2_0.png",
            galleryId = 7,
            mimeType = "image/png",
            width = 512,
            height = 512,
        )
        val decoded = json.decodeFromString<StoredImageArtifact>(json.encodeToString(artifact))
        assertEquals(artifact, decoded)
    }

    @Test
    fun `result round-trips and equals envelope-view fields`() {
        val artifact = StoredImageArtifact(
            artifactId = "img_7",
            path = "/p/1.png",
            uri = "file:///p/1.png",
            galleryId = 7,
            mimeType = "image/png",
            width = 512,
            height = 512,
        )
        val result = ImageToolResult(
            success = true,
            operation = ImageOperation.IMAGE_GENERATION,
            artifacts = listOf(artifact),
            modelId = "cloud:google:imagen",
            providerId = "google",
            executionSource = "cloud",
        )
        val decoded = json.decodeFromString<ImageToolResult>(json.encodeToString(result))
        assertEquals(result, decoded)
        assertEquals(listOf("img_7"), decoded.artifacts.map { it.artifactId })
    }

    @Test
    fun `result carries canonical schema version and error field`() {
        val artifact = StoredImageArtifact(
            artifactId = "img_7", path = "/p/1.png", uri = "file:///p/1.png",
            galleryId = 7, mimeType = "image/png", width = 512, height = 512,
        )
        val result = ImageToolResult(
            success = false,
            operation = ImageOperation.IMAGE_EDIT,
            artifacts = listOf(artifact),
            error = ImageToolError(code = "provider_failed", detail = "boom"),
        )
        assertEquals(1, result.schemaVersion)
        assertEquals("provider_failed", result.error?.code)
        val decoded = json.decodeFromString<ImageToolResult>(json.encodeToString(result))
        assertEquals(result, decoded)
        assertEquals(listOf("img_7"), decoded.artifacts.map { it.artifactId })
    }

    @Test
    fun `artifact id derives deterministically from gallery id`() {
        assertEquals("img_7", ImageToolCatalog.artifactIdFor(7))
        assertEquals(7, ImageToolCatalog.galleryIdFrom("img_7"))
    }

    @Test
    fun `approval table is static`() {
        assertTrue(ImageToolCatalog.requiresApproval("generate_image"))
        assertTrue(ImageToolCatalog.requiresApproval("edit_image"))
        assertFalse(ImageToolCatalog.requiresApproval("analyze_image"))
        assertFalse(ImageToolCatalog.requiresApproval("extract_text_from_image"))
    }
}
