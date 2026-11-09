package me.rerere.locallm.litert

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtBenchmarkAvailabilityTest {
    @Test
    fun gpuBenchmarkRequiresExplicitUserGpuOptIn() {
        assertFalse(canBenchmarkGpu(forceCpu = true, gpuDelegateSupported = true))
    }

    @Test
    fun gpuBenchmarkRequiresAdvertisedNativeDelegateCapability() {
        assertFalse(canBenchmarkGpu(forceCpu = false, gpuDelegateSupported = false))
    }

    @Test
    fun gpuBenchmarkIsAvailableAfterOptInOnACompatibleDevice() {
        assertTrue(canBenchmarkGpu(forceCpu = false, gpuDelegateSupported = true))
    }

    @Test
    fun knownNativeGpuDelegateCompileFailureGetsSpecificMessage() {
        val error = "Failed to create engine: UNKNOWN: ERROR:\\n" +
            "Failed to modify graph with delegate"
        assertTrue(isGpuDelegateCompilationFailure("gpu", error))
        assertTrue(isGpuDelegateCompilationFailure("GPU", error))
    }

    @Test
    fun unrelatedAndCpuFailuresMustNotBeMislabelledGpuFailures() {
        assertFalse(isGpuDelegateCompilationFailure("cpu", "Failed to modify graph with delegate"))
        assertFalse(isGpuDelegateCompilationFailure("gpu", "OutOfMemoryError"))
        assertFalse(isGpuDelegateCompilationFailure("gpu", null))
    }
}
