package me.rerere.locallm.litert.compute

import kotlinx.serialization.json.Json
import me.rerere.locallm.litert.CapabilityGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComputeReceiptTest {

    private val json = Json { encodeDefaults = true }

    private val requirements = ComputeRequirements(AcceleratorPreference.GPU, 1024L, 100L, 100L, 100L)

    @Test
    fun `receipt correlates with the session lifecycle`() {
        val s = ComputeSession.create("c1")
        s.dispatch(ComputeCommand.Load("model-a", requirements))
        s.dispatch(ComputeCommand.Execute("model-a", "infer", requirements = requirements))
        s.observeExecutionCompleted("model-a", "infer", outputBytes = 128L)
        s.dispatch(ComputeCommand.Release("model-a"))

        val receipt = s.buildReceipt(startedAtMs = 100L)
        assertEquals("c1", receipt.session.id)
        assertEquals(listOf("Load", "Execute", "Release"), receipt.commands)
        assertEquals(setOf(ComputeEffect.LOAD, ComputeEffect.EXECUTE, ComputeEffect.RELEASE), receipt.effects)
        assertEquals(emptyList<String>(), receipt.refusals)
        assertEquals(3, receipt.observationCount)
        assertEquals(100L, receipt.startedAtMs)
        assertNull(receipt.completedAtMs)
        assertEquals("RELEASED", receipt.terminalState)
        assertNull(receipt.error)
    }

    @Test
    fun `refusals are recorded in order`() {
        val s = ComputeSession.create("c1")
        s.dispatch(
            ComputeCommand.Load("model-a", ComputeRequirements(AcceleratorPreference.AUTO, 10L * 1024 * 1024 * 1024, 100L, 100L, 0L)),
            capabilities = null,
            availMemBytes = 4L * 1024 * 1024 * 1024,
        )
        s.dispatch(
            ComputeCommand.Execute("model-a", "infer", requirements = requirements),
            granted = CapabilityGrant(
                requestedCapabilities = listOf("compute_infer"),
                grantedCapabilities = listOf("compute_infer"),
                rejectedCapabilities = emptyList(),
            ),
        )
        s.close()

        val receipt = s.buildReceipt(startedAtMs = 1L)
        assertEquals(2, receipt.commands.size)
        assertEquals(emptySet<ComputeEffect>(), receipt.effects)
        assertEquals(
            listOf("compute_memory_denied", "compute_execute_denied"),
            receipt.refusals,
        )
        assertEquals("TERMINATED", receipt.terminalState)
    }

    @Test
    fun `state-level refusals are recorded without committing effects`() {
        val s = ComputeSession.create("c1")
        s.dispatch(ComputeCommand.Load("model-a", requirements))
        s.dispatch(ComputeCommand.Execute("model-a", "infer", requirements = requirements))
        s.dispatch(ComputeCommand.Execute("model-a", "infer", requirements = requirements))
        s.dispatch(ComputeCommand.Shutdown)

        val receipt = s.buildReceipt(startedAtMs = 1L)

        assertEquals(listOf("Load", "Execute", "Execute", "Shutdown"), receipt.commands)
        assertEquals(setOf(ComputeEffect.LOAD, ComputeEffect.EXECUTE, ComputeEffect.SHUTDOWN), receipt.effects)
        assertEquals(listOf("execute not valid in BUSY"), receipt.refusals)
    }

    @Test
    fun `receipt serializes`() {
        val s = ComputeSession.create("c1")
        s.dispatch(ComputeCommand.Shutdown)
        val receipt = s.buildReceipt(startedAtMs = 5L)

        val encoded = json.encodeToString(ComputeReceipt.serializer(), receipt)
        val decoded = json.decodeFromString(ComputeReceipt.serializer(), encoded)
        assertEquals(receipt, decoded)
        assert(encoded.contains("\"c1\""))
    }
}
