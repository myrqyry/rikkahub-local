package me.rerere.rikkahub.data.ai

import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.locallm.AcceleratorProbe
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences
import me.rerere.locallm.litert.LiteRtRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit physical-device smoke test for Gallery's native LiteRT-LM benchmark API
 * through RikkaHub's exclusive runtime gate. No downloads, preferences, or user media.
 *
 * Run via adb install -r (app + test APK) then adb shell am instrument -w -e class ...
 * Do NOT use connectedDebugAndroidTest on an installed app with user data.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtNativeBenchmarkDeviceTest {
    @Test
    fun existingLfmProvidesNativeTokenRatesOnCpu() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
        val model = File(context.filesDir, "local-models/litert/LFM2.5-1.2B-Instruct_int4.litertlm")
        assertTrue("Expected already installed LFM2.5 fixture", model.isFile)
        assertEquals(736015744L, model.length())

        val startedAtMs = SystemClock.elapsedRealtime()
        val summary = withTimeout(240_000L) {
            LiteRtRuntime(context).runNativeBenchmark(
                modelPath = model.absolutePath,
                accelerator = "cpu",
                prefillTokens = 64,
                decodeTokens = 16,
                runs = 1,
            )
        }
        val wallMs = SystemClock.elapsedRealtime() - startedAtMs
        Log.i(
            "LiteRtNativeBenchTest",
            "wallMs=$wallMs runs=${summary.runs} measuredRunMs=${summary.firstRunDurationMs} " +
                "firstTokenMs=${summary.firstTokenMedianMs} " +
                "prefillTps=${summary.prefillMedianTokensPerSecond} " +
                "decodeTps=${summary.decodeMedianTokensPerSecond}",
        )
        assertEquals(1, summary.runs)
        assertTrue("Measured run duration must be positive", summary.firstRunDurationMs > 0.0)
        assertTrue("A single benchmark run cannot exceed its observed surrounding wall time",
            summary.firstRunDurationMs <= wallMs.toDouble() + 50.0)
        assertTrue("Native prefill token speed missing", summary.prefillMedianTokensPerSecond != null)
        assertTrue("Native decode token speed missing", summary.decodeMedianTokensPerSecond != null)
        assertTrue("Native first-token timing missing", summary.firstTokenMedianMs != null)
    }

    @Test
    fun threeRunCpuPresetReportsProgressAndMeasuredMedians() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(
            "Only run against the debuggable package",
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
        val model = File(context.filesDir, "local-models/litert/LFM2.5-1.2B-Instruct_int4.litertlm")
        assertTrue("Expected previously installed LFM2.5 model", model.isFile)
        assertEquals(736015744L, model.length())

        val completedRuns = mutableListOf<Int>()
        val startedMs = SystemClock.elapsedRealtime()
        val summary = withTimeout(420_000L) {
            LiteRtRuntime(context).runNativeBenchmark(
                modelPath = model.absolutePath,
                accelerator = "cpu",
                prefillTokens = 256,
                decodeTokens = 64,
                runs = 3,
                onProgress = { completedRuns += it },
            )
        }
        val elapsedMs = SystemClock.elapsedRealtime() - startedMs

        Log.i(
            "LiteRtNativeBench3",
            "completed=$completedRuns wallMs=$elapsedMs " +
                "firstRunMs=${summary.firstRunDurationMs} " +
                "laterMedianMs=${summary.laterRunMedianMs} " +
                "ttftMedianMs=${summary.firstTokenMedianMs} " +
                "prefillTpsMedian=${summary.prefillMedianTokensPerSecond} " +
                "decodeTpsMedian=${summary.decodeMedianTokensPerSecond}",
        )
        assertEquals(listOf(1, 2, 3), completedRuns)
        assertEquals(3, summary.runs)
        assertTrue("First run duration invalid", summary.firstRunDurationMs > 0.0)
        assertTrue("Later run median missing", (summary.laterRunMedianMs ?: 0.0) > 0.0)
        assertTrue("Native first-token median missing", (summary.firstTokenMedianMs ?: 0.0) > 0.0)
        assertTrue("Native prefill median missing", (summary.prefillMedianTokensPerSecond ?: 0.0) > 0.0)
        assertTrue("Native decode median missing", (summary.decodeMedianTokensPerSecond ?: 0.0) > 0.0)
        assertTrue("First run must fit inside total wall time",
            summary.firstRunDurationMs <= elapsedMs.toDouble() + 50.0)
    }

    @Test
    fun gpuShortRunOnlyWhenUserHasOptedIn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(
            "Only run against debuggable RikkaHub",
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
        assertTrue(
            "GPU test requires user opt-in in LiteRT settings",
            !LocalRuntimePreferences(context).forceCpu(LocalRuntime.LiteRT),
        )
        assertTrue(
            "The device must advertise a LiteRT GPU delegate",
            AcceleratorProbe.probeLiteRtCapabilities(context).gpuDelegateSupported,
        )
        val model = File(context.filesDir, "local-models/litert/LFM2.5-1.2B-Instruct_int4.litertlm")
        assertTrue("Expected already installed model", model.isFile)
        assertEquals(736015744L, model.length())

        val startedMs = SystemClock.elapsedRealtime()
        val summary = withTimeout(180_000L) {
            LiteRtRuntime(context).runNativeBenchmark(
                modelPath = model.absolutePath,
                accelerator = "gpu",
                prefillTokens = 64,
                decodeTokens = 16,
                runs = 1,
            )
        }
        val elapsedMs = SystemClock.elapsedRealtime() - startedMs
        Log.i(
            "LiteRtNativeBenchGpu",
            "wallMs=$elapsedMs firstRunMs=${summary.firstRunDurationMs} " +
                "ttftMs=${summary.firstTokenMedianMs} " +
                "prefillTps=${summary.prefillMedianTokensPerSecond} " +
                "decodeTps=${summary.decodeMedianTokensPerSecond}",
        )
        assertEquals(1, summary.runs)
        assertTrue("GPU reported invalid elapsed run time", summary.firstRunDurationMs > 0.0)
        assertTrue("GPU benchmark exceeds enclosing elapsed time",
            summary.firstRunDurationMs <= elapsedMs.toDouble() + 50.0)
        assertTrue("GPU native prefill rate missing", (summary.prefillMedianTokensPerSecond ?: 0.0) > 0.0)
        assertTrue("GPU native decode rate missing", (summary.decodeMedianTokensPerSecond ?: 0.0) > 0.0)
    }
}
