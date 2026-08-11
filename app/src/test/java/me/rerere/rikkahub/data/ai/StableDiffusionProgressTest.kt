package me.rerere.rikkahub.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StableDiffusionProgressTest {

    @Test
    fun nativeOnProgress_updatesProgressFlow() {
        StableDiffusionBridge.resetProgress()
        StableDiffusionBridge.nativeOnProgress(2, 20, 1.5f)
        assertEquals(
            GenerationProgress(step = 2, totalSteps = 20, elapsedMs = 1500),
            StableDiffusionBridge.progress.value,
        )
    }

    @Test
    fun resetProgress_clearsProgressFlow() {
        StableDiffusionBridge.nativeOnProgress(5, 20, 3.25f)
        assertEquals(GenerationProgress(step = 5, totalSteps = 20, elapsedMs = 3250), StableDiffusionBridge.progress.value)
        StableDiffusionBridge.resetProgress()
        assertNull(StableDiffusionBridge.progress.value)
    }
}