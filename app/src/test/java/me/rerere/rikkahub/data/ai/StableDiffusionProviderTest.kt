package me.rerere.rikkahub.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
