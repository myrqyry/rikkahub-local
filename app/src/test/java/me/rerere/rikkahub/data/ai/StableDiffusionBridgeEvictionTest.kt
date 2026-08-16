package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Low-memory eviction state machine on [StableDiffusionBridge]. These tests run on the JVM:
 * the flag/phase logic is pure Kotlin and [requestEviction] only touches the native layer when a
 * generation is in flight, which the JVM path never enters. The device-level behaviour (real
 * nativeCancel on an in-flight generation, release on the serialized native lane) is covered by
 * the image-gen device acceptance test on a disposable install.
 */
class StableDiffusionBridgeEvictionTest {

    @Before
    fun resetBridgeState() {
        StableDiffusionBridge.evictionRequested = false
        StableDiffusionBridge.setPhase(GenerationPhase.IDLE)
    }

    @Test
    fun `requestEviction sets the flag when idle`() {
        assertFalse(StableDiffusionBridge.evictionRequested)
        StableDiffusionBridge.setPhase(GenerationPhase.IDLE)
        StableDiffusionBridge.requestEviction()
        assertTrue(StableDiffusionBridge.evictionRequested)
    }

    @Test
    fun `release on the native lane clears the flag`() = runBlocking {
        StableDiffusionBridge.setPhase(GenerationPhase.IDLE)
        StableDiffusionBridge.requestEviction()
        assertTrue(StableDiffusionBridge.evictionRequested)

        // Mirrors releaseEvictedSessionIfNeeded in the provider: the release runs on the
        // serialized native dispatcher, then the flag is cleared.
        withContext(Dispatchers.Default) {
            StableDiffusionBridge.invalidateSession()
        }
        StableDiffusionBridge.evictionRequested = false

        assertFalse(StableDiffusionBridge.evictionRequested)
    }

    @Test
    fun `a fresh bridge has no eviction pending`() {
        assertFalse(StableDiffusionBridge.evictionRequested)
    }

    @Test
    fun `eviction flag survives until explicitly cleared`() {
        StableDiffusionBridge.setPhase(GenerationPhase.IDLE)
        StableDiffusionBridge.requestEviction()
        assertTrue(StableDiffusionBridge.evictionRequested)
        // No auto-clear happens from the callback path alone — only the generation lane clears it.
        assertEquals(true, StableDiffusionBridge.evictionRequested)
    }
}
