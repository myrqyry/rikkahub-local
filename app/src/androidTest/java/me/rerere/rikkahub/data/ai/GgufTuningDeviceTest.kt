package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import me.rerere.locallm.llamacpp.GgufTuningStore
import me.rerere.locallm.llamacpp.GgufAutoTuner
import me.rerere.locallm.llamacpp.GgufTuningPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit physical-device GGUF inference acceptance. Fixture staged separately by adb;
 * this test does not touch installed model registration or stored tuning results.
 */
@RunWith(AndroidJUnit4::class)
class GgufTuningDeviceTest {
    @Test fun actualLlamatikThreadSweepProducesEvidenceAndLeavesSettingsUntouched(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = File(context.getExternalFilesDir(null), "gguf-tuner-smoke.gguf")
        assertTrue("Fixture must be separately staged", model.isFile && model.length() > 100_000_000L)
        val originalFingerprint = GgufTuningPolicy.fingerprint(model)
        val result = GgufAutoTuner(context).sweep(model) { completed, total ->
            Log.i(TAG, "SWEEP $completed/$total")
        }
        assertEquals(originalFingerprint, result.fingerprint)
        assertTrue("Must compare several native configurations", result.samples.size >= 3)
        assertEquals(Runtime.getRuntime().availableProcessors(), result.baseline.config.threads)
        assertTrue("Must have real output", result.samples.all { it.producedCodepoints >= 48 })
        assertTrue("Must have measured output rate", result.samples.all { it.codepointsPerSecond > 0.0 })
        assertTrue("Not silently applied", !result.applied)
        result.samples.forEach {
            Log.i(TAG, "RESULT threads=${it.config.threads} firstMs=${it.firstOutputMs} " +
                "charactersPerSecond=${it.codepointsPerSecond} characters=${it.producedCodepoints}")
        }
        Log.i(TAG, "WINNER ${result.winner.config.threads} threads")

        val store = GgufTuningStore(context)
        val testId = "__gguf_device_tuning_fixture_135m__"
        assertTrue("Test ID must not collide with user data", testId !in store.records().first())
        try {
            store.saveMeasurement(testId, result)
            assertTrue("New sweep should remain un-applied",
                store.selected(testId, model)?.applied == false)
            assertNull(store.applied(testId, model))
            store.setApplied(testId, model, true)
            assertEquals(result.winner.config, store.applied(testId, model))
            store.setApplied(testId, model, false)
            assertNull(store.applied(testId, model))
            Log.i(TAG, "APPLY_REVERT_PERSISTENCE_OK")
        } finally {
            // Test-owned key only. Never edit the actual user's registered models or defaults.
            store.deleteMeasurement(testId)
        }
        assertTrue(testId !in store.records().first())
    }

    companion object {
        private const val TAG = "GgufTunerDeviceAcceptance"
    }
}
