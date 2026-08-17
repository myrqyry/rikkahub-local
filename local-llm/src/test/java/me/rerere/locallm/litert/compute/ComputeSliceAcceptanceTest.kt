package me.rerere.locallm.litert.compute

import me.rerere.locallm.AcceleratorProbe
import me.rerere.locallm.litert.CapabilityGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeSliceAcceptanceTest {

    private val caps = AcceleratorProbe.LiteRtCapabilities(
        isQualcomm = true,
        qnnLibrarySupported = true,
        gpuDelegateSupported = true,
        nnapiSupported = true,
        npuSupported = true,
    )

    private val grant = CapabilityGrant(
        requestedCapabilities = listOf("compute_execute"),
        grantedCapabilities = listOf("compute_execute"),
        rejectedCapabilities = emptyList(),
    )

    private fun requirements(accelerator: AcceleratorPreference = AcceleratorPreference.GPU) = ComputeRequirements(
        accelerator = accelerator,
        estimatedModelBytes = 100L * 1024 * 1024,
        maxCpuMillis = 1000L,
        maxGpuMillis = 500L,
        maxAcceleratorMemoryBytes = 1024L * 1024,
    )

    private fun session() = ComputeSession.create("c1")

    @Test
    fun `load flow produces loaded state`() {
        val s = session()
        val observations = s.dispatch(ComputeCommand.Load("model-a", requirements()))
        assertEquals(listOf(ComputeObservation.Loaded("model-a")), observations)
        assertEquals(ComputeSession.State.LOADED, s.state)
    }

    @Test
    fun `execute drives busy then completed via observe`() {
        val s = session()
        s.dispatch(ComputeCommand.Load("model-a", requirements()))
        val started = s.dispatch(ComputeCommand.Execute("model-a", "infer", mapOf("prompt" to "hi"), requirements()))
        assertEquals(listOf(ComputeObservation.ExecutionStarted("model-a", "infer")), started)
        assertEquals(ComputeSession.State.BUSY, s.state)
        val completed = s.observeExecutionCompleted("model-a", "infer", 128L)
        assertEquals(listOf(ComputeObservation.ExecutionCompleted("model-a", "infer", 128L)), completed)
        assertEquals(ComputeSession.State.LOADED, s.state)
    }

    @Test
    fun `execute before load is refused`() {
        val s = session()
        val observations = s.dispatch(ComputeCommand.Execute("model-a", "infer", requirements = requirements()))
        assertEquals(1, observations.size)
        assertTrue((observations.single() as ComputeObservation.CommandRefused).reason.contains("not valid in IDLE"))
        assertEquals(ComputeSession.State.IDLE, s.state)
    }

    @Test
    fun `execution failure transitions back to loaded`() {
        val s = session()
        s.dispatch(ComputeCommand.Load("model-a", requirements()))
        s.dispatch(ComputeCommand.Execute("model-a", "infer", requirements = requirements()))
        val failed = s.observeExecutionFailed("model-a", "infer", "out of memory")
        assertEquals(listOf(ComputeObservation.ExecutionFailed("model-a", "infer", "out of memory")), failed)
        assertEquals(ComputeSession.State.LOADED, s.state)
    }

    @Test
    fun `release terminates the session lifecycle`() {
        val s = session()
        s.dispatch(ComputeCommand.Load("model-a", requirements()))
        val released = s.dispatch(ComputeCommand.Release("model-a"))
        assertEquals(listOf(ComputeObservation.Released("model-a")), released)
        assertEquals(ComputeSession.State.RELEASED, s.state)
        assertTrue(s.isClosed)
    }

    @Test
    fun `shutdown terminates and later commands are refused`() {
        val s = session()
        val observations = s.dispatch(ComputeCommand.Shutdown)
        assertEquals(listOf(ComputeObservation.ShutdownComplete), observations)
        assertEquals(ComputeSession.State.TERMINATED, s.state)
        val later = s.dispatch(ComputeCommand.Load("model-a", requirements()))
        assertEquals(listOf(ComputeObservation.CommandRefused("session closed")), later)
    }

    @Test
    fun `close evicts idempotently`() {
        val s = session()
        val first = s.close()
        assertEquals(listOf(ComputeObservation.Evicted("closed")), first)
        assertEquals(ComputeSession.State.TERMINATED, s.state)
        assertEquals(emptyList<ComputeObservation>(), s.close())
    }

    @Test
    fun `budget invalid is refused by the gate`() {
        val s = session()
        val bad = ComputeCommand.Execute(
            "model-a",
            "infer",
            requirements = requirements().copy(maxGpuMillis = -1L),
        )
        val observations = s.dispatch(bad, granted = grant, capabilities = caps)
        assertEquals(listOf(ComputeObservation.CommandRefused("compute_budget_invalid")), observations)
        assertEquals(ComputeSession.State.IDLE, s.state)
    }

    @Test
    fun `memory denied load is refused by the gate`() {
        val s = session()
        val huge = ComputeCommand.Load(
            "model-a",
            ComputeRequirements(AcceleratorPreference.AUTO, 10L * 1024 * 1024 * 1024, 1000L, 500L, 0L),
        )
        val observations = s.dispatch(huge, granted = null, capabilities = caps, availMemBytes = 4L * 1024 * 1024 * 1024)
        assertEquals(listOf(ComputeObservation.CommandRefused("compute_memory_denied")), observations)
        assertEquals(ComputeSession.State.IDLE, s.state)
    }

    @Test
    fun `interactive execute is allowed without a grant`() {
        val s = session()
        s.dispatch(ComputeCommand.Load("model-a", requirements()))
        val observations = s.dispatch(
            ComputeCommand.Execute("model-a", "infer", requirements = requirements()),
            granted = null,
            capabilities = caps,
        )
        assertEquals(listOf(ComputeObservation.ExecutionStarted("model-a", "infer")), observations)
    }
}
