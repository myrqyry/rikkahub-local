package me.rerere.locallm.litert.compute

import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeCapabilitiesTest {

    // -- pickLiteRt: QNN (Qualcomm) → GPU → NNAPI → CPU --

    @Test fun `LiteRT picks QNN when Qualcomm and QNN library is loadable`() {
        val caps = ComputeCapabilities(
            isQualcomm = true,
            qnnLibrarySupported = true,
            gpuDelegateSupported = true,
            nnapiSupported = true,
        )
        assertEquals("QNN", pickLiteRt(caps))
    }

    @Test fun `LiteRT falls back to GPU on Qualcomm if QNN not loadable`() {
        val caps = ComputeCapabilities(
            isQualcomm = true,
            qnnLibrarySupported = false,
            gpuDelegateSupported = true,
            nnapiSupported = true,
        )
        assertEquals("GPU", pickLiteRt(caps))
    }

    @Test fun `LiteRT picks GPU on non-Qualcomm Mali or Adreno when delegate works`() {
        val caps = ComputeCapabilities(
            isQualcomm = false,
            qnnLibrarySupported = false,
            gpuDelegateSupported = true,
            nnapiSupported = true,
        )
        assertEquals("GPU", pickLiteRt(caps))
    }

    @Test fun `LiteRT falls back to NNAPI when GPU delegate is unavailable`() {
        val caps = ComputeCapabilities(
            isQualcomm = false,
            qnnLibrarySupported = false,
            gpuDelegateSupported = false,
            nnapiSupported = true,
        )
        assertEquals("NNAPI", pickLiteRt(caps))
    }

    @Test fun `LiteRT falls back to CPU when nothing else works`() {
        val caps = ComputeCapabilities(
            isQualcomm = false,
            qnnLibrarySupported = false,
            gpuDelegateSupported = false,
            nnapiSupported = false,
        )
        assertEquals("CPU", pickLiteRt(caps))
    }

    // -- pickTaskAccelerator: NPU first, then GPU, then NNAPI, CPU last --

    @Test fun `task accelerator picks NPU first when supported`() {
        val accel = pickTaskAccelerator(
            ComputeCapabilities(
                isQualcomm = false,
                qnnLibrarySupported = false,
                gpuDelegateSupported = true,
                nnapiSupported = true,
                npuSupported = true,
            )
        )
        assertEquals("NPU", accel)
    }

    @Test fun `task accelerator falls back NPU to GPU then NNAPI then CPU`() {
        val npu = pickTaskAccelerator(
            ComputeCapabilities(false, false, false, false, npuSupported = true)
        )
        assertEquals("NPU", npu)
        val gpu = pickTaskAccelerator(
            ComputeCapabilities(false, false, true, false, npuSupported = false)
        )
        assertEquals("GPU", gpu)
        val nnapi = pickTaskAccelerator(
            ComputeCapabilities(false, false, false, true, npuSupported = false)
        )
        assertEquals("NNAPI", nnapi)
        val cpu = pickTaskAccelerator(
            ComputeCapabilities(false, false, false, false, npuSupported = false)
        )
        assertEquals("CPU", cpu)
    }
}
