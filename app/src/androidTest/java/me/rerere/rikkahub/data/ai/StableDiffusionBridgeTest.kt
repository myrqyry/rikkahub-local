package me.rerere.rikkahub.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StableDiffusionBridgeTest {

    @Test fun `nativeInit fails gracefully with nonexistent model`() {
        val bridge = StableDiffusionBridge
        bridge.ensureLoaded()
        val ok = bridge.nativeInit("/nonexistent/model.gguf", 1)
        assertFalse(ok)
    }

    @Test fun `nativeRelease is safe when not initialized`() {
        StableDiffusionBridge.nativeRelease()
        // Should not crash
    }

    @Test fun `nativeGenerate returns null when not initialized`() {
        val result = StableDiffusionBridge.nativeGenerate("test", "", 512, 512, 1, 7.0f, -1)
        assertNull(result)
    }
}
