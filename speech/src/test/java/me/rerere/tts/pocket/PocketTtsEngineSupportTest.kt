package me.rerere.tts.pocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PocketTtsEngineSupportTest {
    @Test
    fun voiceTokensExtractsFrameCountFromLatentsShape() {
        assertEquals(64, PocketTtsSynthesizer.voiceTokens(longArrayOf(1, 64, 1024)))
        assertEquals(1, PocketTtsSynthesizer.voiceTokens(longArrayOf(1, 1, 1024)))
    }

    @Test
    fun voiceTokensRejectsShapesOutsideModelContract() {
        assertThrows(IllegalArgumentException::class.java) {
            PocketTtsSynthesizer.voiceTokens(longArrayOf(2, 64, 1024))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PocketTtsSynthesizer.voiceTokens(longArrayOf(1, 64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PocketTtsSynthesizer.voiceTokens(longArrayOf(1, 64, 512))
        }
    }

    @Test
    fun frameLimitIsMinOfMaxFramesAndRemainingLmCache() {
        assertEquals(92, PocketTtsSynthesizer.frameLimit(lmCacheLength = 100, voiceTokens = 5, tokenCount = 3, maxFrames = 100))
        assertEquals(9, PocketTtsSynthesizer.frameLimit(lmCacheLength = 100, voiceTokens = 5, tokenCount = 3, maxFrames = 9))
    }

    @Test
    fun frameLimitThrowsWhenConditioningExceedsLmCache() {
        assertThrows(IllegalArgumentException::class.java) {
            PocketTtsSynthesizer.frameLimit(lmCacheLength = 100, voiceTokens = 95, tokenCount = 10, maxFrames = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PocketTtsSynthesizer.frameLimit(lmCacheLength = 100, voiceTokens = 50, tokenCount = 50, maxFrames = 100)
        }
    }
}
