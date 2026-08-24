package me.rerere.locallm.litert.image

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Flux2KleinMemoryPolicyTest {
    @Test
    fun `peak chunk estimate is smaller than total package size`() {
        val policy = Flux2KleinMemoryPolicy(
            activationAllowanceBytes = 1_000_000_000L,
            outputAllowanceBytes = 1_000_000L,
            safetyReserveBytes = 256_000_000L,
        )
        val required = policy.requiredBytes(
            largestGraphBytes = 912_190_032L,
            hostBytes = 54_000_000L,
            outputBytes = 1_000_000L,
        )

        assertTrue(required < 6_200_000_000L)
        assertTrue(policy.fitsIn(required, required))
        assertFalse(policy.fitsIn(required, required - 1))
    }
}
