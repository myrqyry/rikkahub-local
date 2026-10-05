package me.rerere.locallm.litert

import me.rerere.locallm.litert.compute.ComputeCapabilities
import me.rerere.locallm.litert.compute.DeviceExecutionProfile
import me.rerere.locallm.litert.compute.MemoryAdmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtExecutionPlannerTest {
    private val gpuCaps = ComputeCapabilities(
        isQualcomm = false,
        qnnLibrarySupported = false,
        gpuDelegateSupported = true,
        nnapiSupported = true,
    )

    private fun profile(
        totalGiB: Long = 8,
        availableGiB: Long = 4,
        lowMemory: Boolean = false,
        capabilities: ComputeCapabilities = gpuCaps,
    ) = DeviceExecutionProfile(
        totalMemoryBytes = totalGiB * GIB,
        availableMemoryBytes = availableGiB * GIB,
        lowMemory = lowMemory,
        capabilities = capabilities,
    )

    @Test
    fun `cached accelerator is revalidated instead of blindly reused`() {
        val noAccelerators = ComputeCapabilities(
            isQualcomm = false,
            qnnLibrarySupported = false,
            gpuDelegateSupported = false,
            nnapiSupported = false,
        )
        val config = LiteRtModelDefaults.forModelFile(QWEN_FILE)
        val plan = LiteRtExecutionPlanner.plan(
            config = config,
            modelFileBytes = config.sizeBytes,
            profile = profile(capabilities = noAccelerators),
            cachedAccelerator = "GPU",
        )

        assertEquals("CPU", plan.accelerator)
    }

    @Test
    fun `legacy cached NNAPI label is rejected because LiteRT-LM cannot request it`() {
        val nnapiOnly = ComputeCapabilities(
            isQualcomm = false,
            qnnLibrarySupported = false,
            gpuDelegateSupported = false,
            nnapiSupported = true,
        )
        val config = LiteRtModelDefaults.forModelFile(QWEN_FILE)
        val plan = LiteRtExecutionPlanner.plan(
            config = config,
            modelFileBytes = config.sizeBytes,
            profile = profile(capabilities = nnapiOnly),
            cachedAccelerator = "NNAPI",
        )

        assertEquals("CPU", plan.accelerator)
    }

    @Test
    fun `known working cached CPU fallback remains sticky`() {
        val config = LiteRtModelDefaults.forModelFile(QWEN_FILE)
        val plan = LiteRtExecutionPlanner.plan(
            config = config,
            modelFileBytes = config.sizeBytes,
            profile = profile(),
            cachedAccelerator = "CPU",
        )

        assertEquals("CPU", plan.accelerator)
    }

    @Test
    fun `force CPU overrides cached and probed accelerators`() {
        val config = LiteRtModelDefaults.forModelFile(QWEN_FILE)
        val plan = LiteRtExecutionPlanner.plan(
            config = config,
            modelFileBytes = config.sizeBytes,
            profile = profile(),
            cachedAccelerator = "GPU",
            forceCpu = true,
        )

        assertEquals("CPU", plan.accelerator)
    }

    @Test
    fun `model CPU-first preference is honored when there is no prior resolved path`() {
        val config = LiteRtModelDefaults.forModelFile("mobile_actions_q8_ekv1024.litertlm")
        val plan = LiteRtExecutionPlanner.plan(
            config = config,
            modelFileBytes = config.sizeBytes,
            profile = profile(),
        )

        assertEquals("CPU", plan.accelerator)
    }

    @Test
    fun `live available memory is the hard model admission signal`() {
        val config = LiteRtModelDefaults.forModelFile(QWEN_FILE)
        val plan = LiteRtExecutionPlanner.plan(
            config = config,
            modelFileBytes = 2L * GIB,
            profile = profile(availableGiB = 2),
        )

        assertFalse(plan.allowed)
        assertTrue(plan.memoryAdmission is MemoryAdmission.TooLarge)
    }

    @Test
    fun `catalog total-RAM minimum is advisory rather than a hard refusal`() {
        val config = LiteRtModelDefaults.forModelFile(GEMMA_FILE)
        val plan = LiteRtExecutionPlanner.plan(
            config = config,
            modelFileBytes = 512L * 1024L * 1024L,
            profile = profile(totalGiB = 6, availableGiB = 4),
        )

        assertTrue(plan.allowed)
        assertTrue(LiteRtPlanWarning.BELOW_CATALOG_MINIMUM in plan.warnings)
    }

    @Test
    fun `device-aware default prefers Gemma E2B when current memory can carry it`() {
        val recommended = LiteRtExecutionPlanner.recommend(
            entries = LiteRtCatalog.ENTRIES,
            profile = profile(totalGiB = 8, availableGiB = 4),
        )

        assertEquals(GEMMA_FILE, recommended?.modelFile)
    }

    @Test
    fun `device-aware default falls back to Qwen when Gemma does not fit live free RAM`() {
        val recommended = LiteRtExecutionPlanner.recommend(
            entries = LiteRtCatalog.ENTRIES,
            profile = profile(totalGiB = 6, availableGiB = 3),
        )

        assertEquals(QWEN_FILE, recommended?.modelFile)
    }

    @Test
    fun `Android low-memory signal prefers the smaller fitting recommended model`() {
        val recommended = LiteRtExecutionPlanner.recommend(
            entries = LiteRtCatalog.ENTRIES,
            profile = profile(totalGiB = 8, availableGiB = 4, lowMemory = true),
        )

        assertEquals(QWEN_FILE, recommended?.modelFile)
    }

    @Test
    fun plannerNeverReportsNnapiForLiteRtLmWhenGpuUnavailable() {
        val nnapiOnly = ComputeCapabilities(
            isQualcomm = false,
            qnnLibrarySupported = false,
            gpuDelegateSupported = false,
            nnapiSupported = true,
        )
        val config = LiteRtModelDefaults.forModelFile(QWEN_FILE)
        val plan = LiteRtExecutionPlanner.plan(
            config = config,
            modelFileBytes = config.sizeBytes,
            profile = profile(capabilities = nnapiOnly),
        )

        assertEquals("CPU", plan.accelerator)
    }

    companion object {
        private const val GIB = 1024L * 1024L * 1024L
        private const val QWEN_FILE =
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        private const val GEMMA_FILE = "gemma-4-E2B-it.litertlm"
    }
}
