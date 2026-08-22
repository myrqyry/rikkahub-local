package me.rerere.rikkahub.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device acceptance test for the local Stable Diffusion runtime.
 *
 * Flow: ensureLoaded → ensureSession(smallModel, CPU) → nativeGenerate → assert non-null,
 * non-empty RGBA of the expected dimensions → nativeCancel → re-generate on the same warm
 * session → assert the second generation succeeds (warm reuse, no model reload).
 *
 * Skips gracefully (assumeTrue) when no SD model file is installed, so CI runs without a
 * fixture do not hard-fail.
 *
 * DATA-SAFETY: run ONLY on a disposable install or emulator via connectedDebugAndroidTest.
 * NEVER against a phone carrying real user data — AGP uninstalls the target package after an
 * instrumentation run (see docs/references/image-gen-device-acceptance.md).
 */
@RunWith(AndroidJUnit4::class)
class StableDiffusionDeviceAcceptanceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun releaseSession() {
        StableDiffusionBridge.invalidateSession()
    }

    private fun findSmallModelFile(): File? {
        val exts = setOf("gguf", "safetensors", "bin")
        val candidates = mutableListOf<File>()
        fun walk(dir: File) {
            dir.listFiles()?.forEach { f ->
                when {
                    f.isDirectory -> walk(f)
                    f.isFile && f.extension.lowercase() in exts -> candidates += f
                }
            }
        }
        walk(context.filesDir)
        return candidates.minByOrNull { it.length() }
    }

    @Test
    fun `local generation produces expected pixels and warm session regenerates`() {
        val model = findSmallModelFile()
        assumeTrue("No Stable Diffusion model file installed; skipping device acceptance test", model != null)

        StableDiffusionBridge.ensureLoaded()
        assertTrue(
            "Native runtime does not support CPU backend on this device",
            StableDiffusionBridge.nativeSupportsBackend(StableDiffusionBridge.Backend.CPU.value),
        )

        val width = 512
        val height = 512
        val expectedBytes = width.toLong() * height.toLong() * 4L

        // Load once; reject a reload between generations below by asserting the session stays warm.
        assertTrue(
            "ensureSession failed to load ${model!!.name}",
            StableDiffusionBridge.ensureSession(model.absolutePath, StableDiffusionBridge.Backend.CPU),
        )

        // First generation: expected pixel buffer.
        val first = StableDiffusionBridge.nativeGenerate(
            prompt = "a red apple on a wooden table",
            negativePrompt = "",
            width = width,
            height = height,
            steps = 4,
            cfg = 1.0f,
            seed = 42,
        )
        assertNotNull("First generation returned null RGBA", first)
        assertEquals("First generation buffer size", expectedBytes, first!!.size.toLong())
        assertTrue("First generation buffer must not be all zeros", first.any { it != 0.toByte() })

        // Cancel: safe to call after completion (sd.cpp is idle) and must not tear the session.
        StableDiffusionBridge.nativeCancel()

        // Second generation on the same warm session: no reload, still correct pixels.
        assertTrue("Session must still be warm for warm-reuse check", StableDiffusionBridge.isSessionWarm(model.absolutePath, StableDiffusionBridge.Backend.CPU))
        val second = StableDiffusionBridge.nativeGenerate(
            prompt = "a red apple on a wooden table",
            negativePrompt = "",
            width = width,
            height = height,
            steps = 4,
            cfg = 1.0f,
            seed = 43,
        )
        assertNotNull("Warm-session regeneration returned null RGBA", second)
        assertEquals("Warm-session regeneration buffer size", expectedBytes, second!!.size.toLong())
        assertTrue("Warm-session regeneration buffer must not be all zeros", second.any { it != 0.toByte() })
    }
}
