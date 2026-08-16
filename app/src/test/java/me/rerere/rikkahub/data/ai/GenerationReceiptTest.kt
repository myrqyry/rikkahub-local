package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.media.MediaArtifactRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationReceiptTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `receipt round-trips through kotlinx serialization`() {
        val receipt = GenerationReceipt(
            artifactId = "artifact-1",
            modelId = "sd-tiny",
            modelRevision = "abc123",
            runtime = "stable-diffusion.cpp",
            backend = "CPU",
            width = 512,
            height = 512,
            seed = 42L,
            steps = 20,
            cfg = 7.0f,
            sampler = "euler",
            scheduler = "karras",
            elapsedMs = 1234L,
            sourceArtifacts = listOf(
                MediaArtifactRef(artifactId = "ref-1", path = "/a.png"),
                MediaArtifactRef(artifactId = "ref-2", path = "/b.png"),
            ),
        )
        val restored = json.decodeFromString<GenerationReceipt>(json.encodeToString(GenerationReceipt.serializer(), receipt))
        assertEquals(receipt, restored)
    }

    @Test
    fun `receipt round-trips with empty source artifacts`() {
        val receipt = GenerationReceipt(
            artifactId = "artifact-2",
            modelId = "sd-tiny",
            modelRevision = "abc123",
            runtime = "stable-diffusion.cpp",
            backend = "VULKAN",
            width = 768,
            height = 512,
            seed = -1L,
            steps = 25,
            cfg = 8.0f,
            sampler = "dpmpp_2m",
            scheduler = "karras",
            elapsedMs = 500L,
            sourceArtifacts = emptyList(),
        )
        val restored = json.decodeFromString<GenerationReceipt>(json.encodeToString(GenerationReceipt.serializer(), receipt))
        assertEquals(emptyList<MediaArtifactRef>(), restored.sourceArtifacts)
    }

    @Test
    fun `receipt round-trips with null revision`() {
        val receipt = GenerationReceipt(
            artifactId = "artifact-3",
            modelId = "openai-dall-e",
            modelRevision = null,
            runtime = "cloud",
            backend = "cloud",
            width = 1024,
            height = 1024,
            seed = null,
            steps = null,
            cfg = null,
            sampler = null,
            scheduler = null,
            elapsedMs = 890L,
            sourceArtifacts = emptyList(),
        )
        val restored = json.decodeFromString<GenerationReceipt>(json.encodeToString(GenerationReceipt.serializer(), receipt))
        assertNull(restored.modelRevision)
        assertNull(restored.seed)
    }
}
