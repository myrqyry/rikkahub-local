package me.rerere.rikkahub.data.ai

import android.content.Context
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
import me.rerere.locallm.litert.ROLE_ASSISTANT
import me.rerere.locallm.litert.ROLE_USER
import me.rerere.locallm.litert.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Manually executed real-device-only acceptance test for the pinned Huihui Gemma 4 E4B
 * LiteRT-LM model; never run connectedDebugAndroidTest on a phone containing user data.
 * Model is staged in app-owned external files without modifying the user's registered models.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtGemmaE4BDeviceTest {
    @Test
    fun pinnedGemmaGeneratesCorrectAnswerOnCpu() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(
            "Only run against the debuggable package",
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
        val model = File(
            context.getExternalFilesDir(null),
            "local-models/litert/huihui-gemma4-e4b-abliterated.litertlm",
        )
        assertTrue("Gemma test bundle must have been deliberately staged", model.isFile)
        assertEquals("Gemma test bundle exact published length", 4309601504L, model.length())
        val profile = ExecutionProfileProbe.capture(context)
        Log.i(TAG, "RAM total=${profile.totalMemoryBytes} available=${profile.availableMemoryBytes}")
        val runtime = LiteRtRuntime(context)
        val started = SystemClock.elapsedRealtime()
        try {
            withTimeout(240_000L) {
                val loaded = runtime.ensureLoaded(
                    modelPath = model.absolutePath,
                    preferredAccel = "CPU",
                    forceCpu = true,
                    maxNumTokens = 256,
                    supportImage = false,
                    supportAudio = false,
                    speculativeDecoding = false,
                    temperature = 0.1,
                )
                Log.i(TAG, "LOAD backend=${loaded.accelerator} elapsedMs=${SystemClock.elapsedRealtime() - started}")
                assertEquals("CPU", loaded.accelerator)
                val prompt = "What is seven plus five? Reply with only the number."
                val result = runtime.streamTurns(
                    history = listOf(Turn(ROLE_USER, prompt)),
                    coldBlob = prompt,
                ).lastOrNull().orEmpty()
                Log.i(
                    TAG,
                    "RESULT text=${result.take(200)} elapsedMs=${SystemClock.elapsedRealtime() - started}" +
                        " telemetry=${runtime.lastTelemetry}",
                )
                assertTrue("Real model should produce 12, not a fragment or unrelated text: '$result'",
                    Regex("""(?<!\d)12(?!\d)""").containsMatchIn(result))

                // Check a longer answer in the same native Conversation. This exercises
                // the warm KV-cache continuation and multiple streaming fragments.
                val followUp = "Name the process plants use to turn sunlight into food. Reply with one word."
                val nextResult = runtime.streamTurns(
                    history = listOf(
                        Turn(ROLE_USER, prompt),
                        Turn(ROLE_ASSISTANT, result),
                        Turn(ROLE_USER, followUp),
                    ),
                    coldBlob = followUp,
                ).lastOrNull().orEmpty()
                Log.i(TAG, "FOLLOW_UP text=${nextResult.take(240)} elapsedMs=${SystemClock.elapsedRealtime() - started}")
                assertTrue("Second native turn should answer photosynthesis: '$nextResult'",
                    nextResult.contains("photosynthesis", ignoreCase = true))
            }
        } finally {
            withContext(NonCancellable) {
                runtime.closeIfLoaded()
                Log.i(TAG, "RELEASE elapsedMs=${SystemClock.elapsedRealtime() - started}")
            }
        }
    }

    companion object { private const val TAG = "GemmaE4BDeviceAcceptance" }
}
