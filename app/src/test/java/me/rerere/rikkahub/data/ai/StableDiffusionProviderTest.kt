package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.GeneratedImagePayload
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.locallm.SdCatalog
import me.rerere.locallm.SdGenerationProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StableDiffusionProviderTest {
    @Test
    fun `accepts normal mobile generation settings`() {
        assertNull(stableDiffusionRequestError(512, 512, 20, 7.0f))
        assertNull(stableDiffusionRequestError(1024, 768, 4, 1.0f))
    }

    @Test
    fun `rejects invalid dimensions before JNI`() {
        assertEquals(
            "Image width and height must be between 64 and 2048 pixels.",
            stableDiffusionRequestError(32, 512, 20, 7.0f),
        )
        assertEquals(
            "Image width and height must be multiples of 8.",
            stableDiffusionRequestError(513, 512, 20, 7.0f),
        )
    }

    @Test
    fun `rejects invalid sampling settings before JNI`() {
        assertEquals(
            "Sampling steps must be between 1 and 200.",
            stableDiffusionRequestError(512, 512, 0, 7.0f),
        )
        assertEquals(
            "CFG scale must be a finite value between 0 and 50.",
            stableDiffusionRequestError(512, 512, 20, Float.NaN),
        )
    }

    @Test
    fun `no profile keeps provider values untouched`() {
        val provider = ProviderSetting.StableDiffusion(
            width = 768,
            height = 768,
            steps = 8,
            cfgScale = 2.5f,
        )
        val effective = resolveEffectiveGenerationParams(provider, null)
        assertEquals(
            EffectiveGenerationParams(768, 768, 8, 2.5f),
            effective,
        )
    }

    @Test
    fun `model profile replaces factory defaults`() {
        val provider = ProviderSetting.StableDiffusion()
        val profile = SdGenerationProfile(
            defaultWidth = 512,
            defaultHeight = 512,
            minSteps = 1,
            maxSteps = 4,
            defaultSteps = 1,
            defaultCfgScale = 0f,
        )
        val effective = resolveEffectiveGenerationParams(provider, profile)
        assertEquals(EffectiveGenerationParams(512, 512, 1, 0f), effective)
    }

    @Test
    fun `user overrides win over model profile`() {
        val provider = ProviderSetting.StableDiffusion(
            width = 768,
            height = 768,
            steps = 4,
            cfgScale = 2.5f,
        )
        val profile = SdGenerationProfile(defaultSteps = 1, defaultCfgScale = 0f)
        val effective = resolveEffectiveGenerationParams(provider, profile)
        assertEquals(EffectiveGenerationParams(768, 768, 4, 2.5f), effective)
    }

    @Test
    fun `sdxl turbo profile defaults to 1024 resolution`() {
        val provider = ProviderSetting.StableDiffusion()
        val profile = SdGenerationProfile(
            defaultWidth = 1024,
            defaultHeight = 1024,
            minSteps = 1,
            maxSteps = 4,
            defaultSteps = 1,
            defaultCfgScale = 0f,
        )
        val effective = resolveEffectiveGenerationParams(provider, profile)
        assertEquals(EffectiveGenerationParams(1024, 1024, 1, 0f), effective)
    }

    @Test
    fun `catalog entries carry verified turbo profiles`() {
        val sdturbo = SdCatalog.findByModelFile("stable-diffusion-v2-1-turbo-Q8_0.gguf")
        val sdxl = SdCatalog.findByModelFile("stable-diffusion-xl-1.0-turbo-Q8_0.gguf")
        assertTrue(sdturbo?.generationProfile != null)
        assertTrue(sdxl?.generationProfile != null)
        for (entry in SdCatalog.ENTRIES) {
            val profile = entry.generationProfile
            assertTrue("entry ${entry.modelFile} missing profile", profile != null)
            assertEquals(1, profile!!.minSteps)
            assertEquals(4, profile.maxSteps)
            assertEquals(1, profile.defaultSteps)
            assertEquals(0f, profile.defaultCfgScale, 0f)
        }
        assertEquals(512, sdturbo!!.generationProfile!!.defaultWidth)
        assertEquals(512, sdturbo.generationProfile!!.defaultHeight)
        assertEquals(1024, sdxl!!.generationProfile!!.defaultWidth)
        assertEquals(1024, sdxl.generationProfile!!.defaultHeight)
        assertNull(SdCatalog.findByModelFile("unknown.gguf"))
    }

    @Test
    fun `memory policy skips unknown or nonpositive inputs`() {
        assertNull(sdMemoryPolicyViolation(0L, 512, 512, 8L * 1024 * 1024 * 1024))
        assertNull(sdMemoryPolicyViolation(2_000_000_000L, 0, 512, 8L * 1024 * 1024 * 1024))
        assertNull(sdMemoryPolicyViolation(2_000_000_000L, 512, 512, 0L))
    }

    @Test
    fun `memory policy allows fits-and-boundary`() {
        val deviceRam = 8L * 1024 * 1024 * 1024
        assertNull(sdMemoryPolicyViolation(2_000_000_000L, 512, 512, deviceRam))
        assertNull(sdMemoryPolicyViolation(8L * 1024 * 1024 * 1024, 1, 1, 8L * 1024 * 1024 * 1024 + 1024))
    }

    @Test
    fun `memory policy refuses model larger than ram`() {
        val message = sdMemoryPolicyViolation(12L * 1024 * 1024 * 1024, 512, 512, 8L * 1024 * 1024 * 1024)
        assertTrue(message != null)
        assertTrue(message!!.contains("Use a smaller model or image size"))
    }

    @Test
    fun `memory policy refuses model plus buffers exceeding ram`() {
        val deviceRam = 4L * 1024 * 1024 * 1024
        val model = 4L * 1024 * 1024 * 1024 - 1
        val message = sdMemoryPolicyViolation(model, 1024, 1024, deviceRam)
        assertTrue(message != null)
        assertTrue(message!!.contains("needs roughly"))
    }

    @Test
    fun `format memory size renders mb and gb`() {
        assertEquals("0 MB", formatMemorySize(0L))
        assertEquals("1 MB", formatMemorySize(1024L * 1024))
        assertEquals("1.00 GB", formatMemorySize(1024L * 1024 * 1024))
        assertEquals("2.16 GB", formatMemorySize(2_320_000_000L))
    }

    @Test
    fun `aspectRatio selects profile-aware dimensions`() {
        val profile = SdGenerationProfile(
            defaultWidth = 768,
            defaultHeight = 512,
            minSteps = 1,
            maxSteps = 4,
            defaultSteps = 1,
            defaultCfgScale = 0f,
        )
        assertEquals(768 to 512, resolveAspectDimensions(ImageAspectRatio.SQUARE, profile))
        assertEquals(768 to 512, resolveAspectDimensions(ImageAspectRatio.LANDSCAPE, profile))
        assertEquals(512 to 768, resolveAspectDimensions(ImageAspectRatio.PORTRAIT, profile))
    }

    @Test
    fun `aspectRatio falls back to 512 square without a profile`() {
        assertEquals(512 to 512, resolveAspectDimensions(ImageAspectRatio.SQUARE, null))
        assertEquals(512 to 512, resolveAspectDimensions(ImageAspectRatio.LANDSCAPE, null))
        assertEquals(512 to 512, resolveAspectDimensions(ImageAspectRatio.PORTRAIT, null))
    }

    @Test
    fun `numOfImages emits that many items`() = runBlocking {
        val items = generateSerially(count = 3) { index ->
            ImageGenerationItem(payload = GeneratedImagePayload.Base64("png-$index", "image/png"))
        }.toList()
        assertEquals(3, items.size)
        assertEquals(listOf("png-0", "png-1", "png-2"), items.map { (it.payload as GeneratedImagePayload.Base64).data })
    }

    @Test
    fun `numOfImages of one emits a single item`() = runBlocking {
        val items = generateSerially(count = 1) {
            ImageGenerationItem(payload = GeneratedImagePayload.Base64("only", "image/png"))
        }.toList()
        assertEquals(1, items.size)
        assertEquals("only", (items.single().payload as GeneratedImagePayload.Base64).data)
    }

    @Test
    fun `stable diffusion provider defaults to cpu backend`() {
        assertFalse(ProviderSetting.StableDiffusion().useVulkan)
    }
}
