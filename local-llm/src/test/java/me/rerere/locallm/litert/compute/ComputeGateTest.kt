package me.rerere.locallm.litert.compute

import me.rerere.locallm.litert.CapabilityGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeGateTest {

    private val gate = ComputeGate()

    private val caps = ComputeCapabilities(
        isQualcomm = true,
        qnnLibrarySupported = true,
        gpuDelegateSupported = true,
        nnapiSupported = true,
        npuSupported = true,
    )

    private val load = ComputeCommand.Load(
        modelId = "model-a",
        requirements = ComputeRequirements(AcceleratorPreference.AUTO, 100L * 1024 * 1024, 1000L, 500L, 1024L * 1024),
    )

    private fun execute(accelerator: AcceleratorPreference = AcceleratorPreference.GPU) = ComputeCommand.Execute(
        modelId = "model-a",
        operation = "infer",
        requirements = ComputeRequirements(accelerator, 100L * 1024 * 1024, 1000L, 500L, 1024L * 1024),
    )

    @Test
    fun `auto accelerator resolves through the probe`() {
        assertEquals("qnn", gate.resolveAccelerator(load.requirements, caps))
        val decision = gate.evaluate(load, granted = null, capabilities = caps, availMemBytes = 4L * 1024 * 1024 * 1024)
        assertTrue(decision.allowed)
        assertEquals(ComputeEffect.LOAD, decision.effect)
    }

    @Test
    fun `load is refused when memory admission fails`() {
        val huge = ComputeCommand.Load(
            modelId = "model-a",
            requirements = ComputeRequirements(AcceleratorPreference.AUTO, 10L * 1024 * 1024 * 1024, 1000L, 500L, 0L),
        )
        val decision = gate.evaluate(huge, granted = null, capabilities = caps, availMemBytes = 4L * 1024 * 1024 * 1024)
        assertFalse(decision.allowed)
        assertEquals("compute_memory_denied", decision.reason)
    }

    @Test
    fun `execute is refused on invalid budget`() {
        val badBudget = execute().let {
            it.copy(requirements = it.requirements.copy(maxGpuMillis = -1L))
        }
        val decision = gate.evaluate(badBudget, granted = null, capabilities = caps, availMemBytes = 0L)
        assertFalse(decision.allowed)
        assertEquals("compute_budget_invalid", decision.reason)
    }

    @Test
    fun `execute is refused when accelerator cannot be resolved`() {
        val auto = execute(AcceleratorPreference.AUTO)
        val decision = gate.evaluate(auto, granted = null, capabilities = null, availMemBytes = 0L)
        assertFalse(decision.allowed)
        assertEquals("compute_accelerator_unknown", decision.reason)
    }

    @Test
    fun `execute is denied when the grant lacks compute_execute`() {
        val grant = CapabilityGrant(
            requestedCapabilities = listOf("compute_infer"),
            grantedCapabilities = listOf("compute_infer"),
            rejectedCapabilities = emptyList(),
        )
        val decision = gate.evaluate(execute(), granted = grant, capabilities = caps, availMemBytes = 0L)
        assertFalse(decision.allowed)
        assertEquals("compute_execute_denied", decision.reason)
    }

    @Test
    fun `execute is allowed with a grant`() {
        val grant = CapabilityGrant(
            requestedCapabilities = listOf("compute_execute"),
            grantedCapabilities = listOf("compute_execute"),
            rejectedCapabilities = emptyList(),
        )
        val decision = gate.evaluate(execute(), granted = grant, capabilities = caps, availMemBytes = 0L)
        assertTrue(decision.allowed)
        assertEquals(ComputeEffect.EXECUTE, decision.effect)
    }

    @Test
    fun `interactive execute is allowed when no grant is supplied`() {
        val decision = gate.evaluate(execute(), granted = null, capabilities = caps, availMemBytes = 0L)
        assertTrue(decision.allowed)
    }

    @Test
    fun `execute is denied when an empty grant is supplied`() {
        val decision = gate.evaluate(execute(), granted = CapabilityGrant(emptyList(), emptyList(), emptyList()), capabilities = caps, availMemBytes = 0L)
        assertFalse(decision.allowed)
        assertEquals("compute_execute_denied", decision.reason)
    }

    @Test
    fun `release and shutdown are always allowed`() {
        val release = gate.evaluate(ComputeCommand.Release("model-a"), null, caps, 0L)
        assertTrue(release.allowed)
        assertEquals(ComputeEffect.RELEASE, release.effect)
        val shutdown = gate.evaluate(ComputeCommand.Shutdown, null, caps, 0L)
        assertTrue(shutdown.allowed)
        assertEquals(ComputeEffect.SHUTDOWN, shutdown.effect)
    }

    @Test
    fun `cpu preference resolves without capabilities`() {
        val cpu = execute(AcceleratorPreference.CPU)
        assertEquals("cpu", gate.resolveAccelerator(cpu.requirements, null))
        val decision = gate.evaluate(cpu, granted = null, capabilities = null, availMemBytes = 0L)
        assertTrue(decision.allowed)
        assertNotNull(decision.effect)
    }

    @Test
    fun `explicit gpu preference falls back when the snapshot lacks gpu`() {
        val noGpu = ComputeCapabilities(
            isQualcomm = false,
            qnnLibrarySupported = false,
            gpuDelegateSupported = false,
            nnapiSupported = true,
        )
        assertEquals("nnapi", gate.resolveAccelerator(execute(AcceleratorPreference.GPU).requirements, noGpu))
        // Without a snapshot the request is honoured at face value.
        assertEquals("gpu", gate.resolveAccelerator(execute(AcceleratorPreference.GPU).requirements, null))
    }

    @Test
    fun `explicit npu preference falls back when the snapshot lacks npu`() {
        val noNpu = ComputeCapabilities(
            isQualcomm = true,
            qnnLibrarySupported = true,
            gpuDelegateSupported = true,
            nnapiSupported = true,
            npuSupported = false,
        )
        assertEquals("qnn", gate.resolveAccelerator(execute(AcceleratorPreference.NPU).requirements, noNpu))
    }

    @Test
    fun `explicit qnn preference falls back when qualcomm qnn is unavailable`() {
        val noQnn = ComputeCapabilities(
            isQualcomm = true,
            qnnLibrarySupported = false,
            gpuDelegateSupported = true,
            nnapiSupported = true,
        )
        assertEquals("gpu", gate.resolveAccelerator(execute(AcceleratorPreference.QNN).requirements, noQnn))
    }
}
