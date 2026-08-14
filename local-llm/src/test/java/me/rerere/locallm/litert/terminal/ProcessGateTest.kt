package me.rerere.locallm.litert.terminal

import kotlinx.coroutines.runBlocking
import me.rerere.locallm.litert.CapabilityGrant
import me.rerere.locallm.litert.CapabilityScopes
import me.rerere.locallm.litert.FileScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessGateTest {

    private fun grant(granted: List<String>, scopes: CapabilityScopes = CapabilityScopes()) =
        CapabilityGrant(requestedCapabilities = granted, grantedCapabilities = granted, rejectedCapabilities = emptyList(), scopes = scopes)

    private fun allowed(result: ProcessGateResult): ProcessEffectPlan =
        (result.decision as ProcessGate.Decision.Allowed).plan

    private fun denied(result: ProcessGateResult): String =
        (result.decision as ProcessGate.Decision.Denied).reason

    @Test
    fun `allows clean command with file grants`() = runBlocking {
        val g = grant(listOf("process_execute"), CapabilityScopes(fileScopes = listOf(FileScope("a.txt", "read"))))
        val r = ProcessGate(g).evaluate(listOf("cat", "a.txt"), null)
        assertTrue(r.decision is ProcessGate.Decision.Allowed)
        val plan = allowed(r)
        assertTrue(ProcessEffect.READ_LOCAL_DATA in plan.effects)
        assertEquals(setOf("a.txt"), plan.reads)
    }

    @Test
    fun `allows echo with no side effects`() = runBlocking {
        val g = grant(emptyList())
        val r = ProcessGate(g).evaluate(listOf("echo", "hello"), null)
        assertTrue(r.decision is ProcessGate.Decision.Allowed)
        assertTrue(allowed(r).effects.isEmpty())
    }

    @Test
    fun `denies native execution when process_execute ungranted`() = runBlocking {
        val g = grant(emptyList())
        val r = ProcessGate(g).evaluate(listOf("git", "status"), null)
        assertTrue(r.decision is ProcessGate.Decision.Denied)
        assertEquals("native_execution_denied", denied(r))
    }

    @Test
    fun `denies network when process_network ungranted`() = runBlocking {
        // process_execute granted but process_network missing.
        val g = grant(listOf("process_execute"))
        val r = ProcessGate(g).evaluate(listOf("curl", "https://example.com"), null)
        assertTrue(r.decision is ProcessGate.Decision.Denied)
        assertEquals("network_denied", denied(r))
    }

    @Test
    fun `denies write outside file scope`() = runBlocking {
        val g = grant(listOf("process_execute"), CapabilityScopes(fileScopes = listOf(FileScope("/allowed", "write"))))
        val r = ProcessGate(g).evaluate(listOf("echo", "x", ">", "out.txt"), null)
        assertTrue(r.decision is ProcessGate.Decision.Denied)
        assertEquals("write_not_allowed: out.txt", denied(r))
    }

    @Test
    fun `denies read outside file scope`() = runBlocking {
        val g = grant(emptyList(), CapabilityScopes(fileScopes = listOf(FileScope("/allowed", "read"))))
        val r = ProcessGate(g).evaluate(listOf("cat", "secret.txt"), null)
        assertTrue(r.decision is ProcessGate.Decision.Denied)
        assertEquals("read_not_allowed: secret.txt", denied(r))
    }

    @Test
    fun `digest is deterministic`() {
        val a = ProcessEffectPlan.of(ProcessRef("p"), listOf("echo", "hi"), emptySet(), emptySet(), emptySet(), false, false)
        val b = ProcessEffectPlan.of(ProcessRef("p"), listOf("echo", "hi"), emptySet(), emptySet(), emptySet(), false, false)
        assertEquals(a.digest(), b.digest())
        assertEquals(a.commandDigest, b.commandDigest)
    }

    @Test
    fun `digest changes when command or effects change`() {
        val base = ProcessEffectPlan.of(
            ProcessRef("p"), listOf("cat", "a.txt"), setOf(ProcessEffect.READ_LOCAL_DATA), setOf("a.txt"), emptySet(), false, false,
        )
        val differentCommand = ProcessEffectPlan.of(
            ProcessRef("p"), listOf("cat", "b.txt"), setOf(ProcessEffect.READ_LOCAL_DATA), setOf("b.txt"), emptySet(), false, false,
        )
        val differentEffects = ProcessEffectPlan.of(
            ProcessRef("p"), listOf("cat", "a.txt"),
            setOf(ProcessEffect.READ_LOCAL_DATA, ProcessEffect.WRITE_LOCAL_DATA), setOf("a.txt"), setOf("out.txt"), false, false,
        )
        assertNotEquals(base.digest(), differentCommand.digest())
        assertNotEquals(base.digest(), differentEffects.digest())
    }
}
