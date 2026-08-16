package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.ImageCapabilities
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.imageCapabilities
import me.rerere.ai.ui.ImageAspectRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCapabilitiesTest {

    @Test
    fun `local provider exposes bounded capabilities`() {
        val caps: ImageCapabilities = ProviderSetting.StableDiffusion().imageCapabilities
        assertTrue(caps.generation)
        assertFalse(caps.editing)
        assertEquals(4, caps.maxOutputs)
        assertFalse(caps.supportsPartialPreview)
        assertEquals(0, caps.maxReferenceImages)
        assertEquals(ImageAspectRatio.entries.toSet(), caps.supportedAspectRatios)
    }

    @Test
    fun `capability filter hides unsupported aspect ratios`() {
        val caps = ImageCapabilities(
            generation = true,
            editing = true,
            maxOutputs = 4,
            supportedAspectRatios = setOf(ImageAspectRatio.SQUARE, ImageAspectRatio.PORTRAIT),
            supportsSeed = true,
            supportsNegativePrompt = true,
            supportsSteps = true,
            supportsCfg = true,
            supportsPartialPreview = true,
            maxReferenceImages = 4,
        )
        val filtered = caps.filterAspectRatios(
            setOf(ImageAspectRatio.SQUARE, ImageAspectRatio.LANDSCAPE, ImageAspectRatio.PORTRAIT),
        )
        assertEquals(setOf(ImageAspectRatio.SQUARE, ImageAspectRatio.PORTRAIT), filtered)
        assertFalse(ImageAspectRatio.LANDSCAPE in filtered)
    }
}
