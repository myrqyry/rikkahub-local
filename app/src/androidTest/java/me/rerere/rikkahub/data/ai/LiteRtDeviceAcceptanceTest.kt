package me.rerere.rikkahub.data.ai

import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.rerere.locallm.ExecutionProfileProbe
import me.rerere.locallm.litert.LiteRtRuntime
import me.rerere.locallm.litert.ROLE_USER
import me.rerere.locallm.litert.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real installed-model LiteRT-LM acceptance test. Runs against the existing app-private
 * LFM2.5 bundle; it neither downloads an artifact nor writes app preferences.
 *
 * Run ONLY by manually installing the app and test APKs with adb install -r, then
 * adb shell am instrument -w -e class
 *   me.rerere.rikkahub.data.ai.LiteRtDeviceAcceptanceTest
 *   excp.rikkahub.local.debug.test/androidx.test.runner.AndroidJUnitRunner
 *
 * NEVER invoke connectedDebugAndroidTest on a device with user data: the Gradle task can
 * uninstall the target app during cleanup. The manual instrument command does not.
 *
 * This verifies actual CPU model load, one generation, telemetry, and release on THIS
 * installed binary and THIS phone. It does not certify the source model revision,
 * GPU execution, a browser path, or semantic correctness against a reference.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtDeviceAcceptanceTest {
    @Test
    fun existingLfmLoadsGeneratesAndReleasesOnCpu() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(
            "Only run against the debuggable package",
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )

        val model = File(context.filesDir, "local-models/litert/LFM2.5-1.2B-Instruct_int4.litertlm")
        assertTrue("Expected already installed LFM2.5 file", model.isFile)
        assertEquals("Expected installed LFM file size", 736015744L, model.length())

        val profile = ExecutionProfileProbe.capture(context)
        Log.i(
            TAG,
            "PROFILE totalRam=${profile.totalMemoryBytes} freeRam=${profile.availableMemoryBytes} " +
                "lowMemory=${profile.lowMemory} modelBytes=${model.length()}",
        )

        val runtime = LiteRtRuntime(context)
        val started = SystemClock.elapsedRealtime()
        try {
            withTimeout(240_000L) {
                val outcome = runtime.ensureLoaded(
                    modelPath = model.absolutePath,
                    preferredAccel = "CPU",
                    forceCpu = true,
                    maxNumTokens = 256,
                    supportImage = false,
                    supportAudio = false,
                    speculativeDecoding = false,
                    temperature = 0.7,
                )
                Log.i(TAG, "LOAD backend=${outcome.accelerator} elapsedMs=${SystemClock.elapsedRealtime() - started}")
                assertEquals("This acceptance fixture intentionally uses CPU", "CPU", outcome.accelerator)

                val prompt = "What color is a ripe orange? Reply with the color name."
                val text = runtime.streamTurns(
                    history = listOf(Turn(ROLE_USER, prompt)),
                    coldBlob = prompt,
                ).lastOrNull().orEmpty()
                val telemetry = runtime.lastTelemetry
                Log.i(
                    TAG,
                    "RESULT chars=${text.length} sample=${text.take(120)} " +
                        "prefillMs=${telemetry?.prefillMs} decodeMs=${telemetry?.decodeMs} " +
                        "totalElapsedMs=${SystemClock.elapsedRealtime() - started}",
                )
                assertTrue(
                    "Native generation must answer the simple color question, not just emit punctuation: '$text'",
                    text.contains("orange", ignoreCase = true),
                )
                assertTrue("Runtime must record per-turn timing", telemetry != null)
            }
        } finally {
            withContext(NonCancellable) {
                runtime.closeIfLoaded()
                Log.i(TAG, "RELEASE complete elapsedMs=${SystemClock.elapsedRealtime() - started}")
            }
        }
    }

    companion object {
        private const val TAG = "LiteRtDeviceAcceptance"
    }
}
