package me.rerere.locallm.litert.terminal

import kotlinx.serialization.json.Json
import me.rerere.locallm.litert.artifact.ArtifactKind
import me.rerere.locallm.litert.artifact.ArtifactRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessReceiptTest {

    private val json = Json { encodeDefaults = true }

    private fun receipt() = ProcessReceipt(
        process = ProcessRef("p1"),
        command = listOf("git", "status"),
        commandDigest = "abc123",
        effects = setOf("EXECUTE_CODE"),
        reads = emptyList(),
        writes = emptyList(),
        network = false,
        nativeExecution = true,
        startedAtMs = 1L,
        completedAtMs = 2L,
        exitCode = 0,
        termination = "NORMAL",
        outputBytes = 123L,
        outputTruncated = false,
    )

    @Test
    fun `digest is stable across identical plans`() {
        val a = ProcessEffectPlan.of(
            ProcessRef("p"), listOf("git", "status"), setOf(ProcessEffect.EXECUTE_CODE), emptySet(), emptySet(), false, true,
        )
        val b = ProcessEffectPlan.of(
            ProcessRef("p"), listOf("git", "status"), setOf(ProcessEffect.EXECUTE_CODE), emptySet(), emptySet(), false, true,
        )
        assertEquals(a.digest(), b.digest())
        assertEquals(a.commandDigest, b.commandDigest)
        assertTrue(a.commandDigest.isNotEmpty())
    }

    @Test
    fun `serialization round-trips`() {
        val r = receipt()
        val encoded = json.encodeToString(ProcessReceipt.serializer(), r)
        val decoded = json.decodeFromString(ProcessReceipt.serializer(), encoded)
        assertEquals(r, decoded)
    }

    @Test
    fun `round-trips with output ref`() {
        val r = receipt().copy(
            outputRef = ArtifactRef(
                id = "out-1",
                kind = ArtifactKind.PROCESS_OUTPUT,
                name = "git-status.log",
                mimeType = "text/plain",
            ),
        )
        val encoded = json.encodeToString(ProcessReceipt.serializer(), r)
        val decoded = json.decodeFromString(ProcessReceipt.serializer(), encoded)
        assertEquals(r, decoded)
        assertEquals("out-1", decoded.outputRef?.id)
        assertEquals(ArtifactKind.PROCESS_OUTPUT, decoded.outputRef?.kind)
    }

    @Test
    fun `round-trips without output ref`() {
        val r = receipt()
        val encoded = json.encodeToString(ProcessReceipt.serializer(), r)
        val decoded = json.decodeFromString(ProcessReceipt.serializer(), encoded)
        assertEquals(r, decoded)
        assertNull(decoded.outputRef)
    }
}
