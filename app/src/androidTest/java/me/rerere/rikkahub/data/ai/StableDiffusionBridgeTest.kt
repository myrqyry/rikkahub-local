package me.rerere.rikkahub.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StableDiffusionBridgeTest {

    @Test
    fun cpuBackendIsAlwaysAvailable() {
        StableDiffusionBridge.ensureLoaded()
        assertTrue(
            StableDiffusionBridge.nativeSupportsBackend(
                StableDiffusionBridge.Backend.CPU.value,
            )
        )
    }

    @Test
    fun unknownBackendIsRejected() {
        StableDiffusionBridge.ensureLoaded()
        assertFalse(StableDiffusionBridge.nativeSupportsBackend(999))
    }

    @Test
    fun nativeInitFailsGracefullyWithNonexistentModel() {
        val bridge = StableDiffusionBridge
        bridge.ensureLoaded()
        val ok = bridge.nativeInit(
            "/nonexistent/model.gguf",
            StableDiffusionBridge.Backend.CPU.value,
        )
        assertFalse(ok)
    }

    @Test
    fun nativeReleaseIsSafeWhenNotInitialized() {
        StableDiffusionBridge.ensureLoaded()
        StableDiffusionBridge.nativeRelease()
        StableDiffusionBridge.nativeRelease()
    }

    @Test
    fun nativeCancelIsSafeWhenNotInitialized() {
        StableDiffusionBridge.ensureLoaded()
        StableDiffusionBridge.nativeCancel()
    }

    @Test
    fun nativeGenerateReturnsNullWhenNotInitialized() {
        StableDiffusionBridge.ensureLoaded()
        val result = StableDiffusionBridge.nativeGenerate("test", "", 512, 512, 1, 7.0f, -1)
        assertNull(result)
    }
}
