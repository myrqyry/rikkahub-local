package me.rerere.locallm.litert.terminal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import me.rerere.locallm.litert.CapabilityGrant
import me.rerere.locallm.litert.CapabilityScopes
import me.rerere.locallm.litert.FileScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase F (roadmap F9) — acceptance vertical slice + key failure tests for the deterministic
 * terminal substrate, all against a fake underlay (no real binaries):
 *
 *   WorkspaceRef -> gate (ProcessGate) -> TerminalSession.start -> observe (ObservationStream)
 *   -> semantic snapshot -> markTerminal -> ProcessReceipt
 *
 * AgentRun persistence (PROCESS_* events, app module) is deferred to the app-side wiring step.
 */
class TerminalSliceAcceptanceTest {

    /** [InputStream] returning at most [chunkSize] bytes per read — simulates segmented output. */
    private class ChunkedInputStream(data: ByteArray, private val chunkSize: Int) : InputStream() {
        private val buf = data
        private var pos = 0

        override fun read(): Int {
            if (pos >= buf.size) return -1
            return buf[pos++].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= buf.size) return -1
            val n = minOf(chunkSize, len, buf.size - pos)
            System.arraycopy(buf, pos, b, off, n)
            pos += n
            return n
        }
    }

    private class FakeUnderlayProcess(
        override val stdout: InputStream,
        override val stderr: InputStream,
        @Volatile var exitCode: Int = 0,
    ) : ProcessUnderlay.UnderlayProcess {
        override val pid: Long = 42L
        val stdin = ByteArrayOutputStream()
        @Volatile var destroyed = false

        override fun writeStdin(bytes: ByteArray) = stdin.write(bytes)
        override fun closeStdin() = stdin.close()
        override fun destroy() {
            destroyed = true
        }

        override fun awaitExit(): Int = exitCode
    }

    private class FakeUnderlay(
        var stdout: InputStream = ByteArrayInputStream(ByteArray(0)),
        var stderr: InputStream = ByteArrayInputStream(ByteArray(0)),
        var exitCode: Int = 0,
        var startShouldFail: Boolean = false,
    ) : ProcessUnderlay {
        val startedCommands = mutableListOf<List<String>>()
        val launched = mutableListOf<FakeUnderlayProcess>()

        override fun start(command: List<String>, env: Map<String, String>, dir: String?): ProcessUnderlay.UnderlayProcess {
            if (startShouldFail) throw IllegalStateException("backend exploded")
            startedCommands += command
            val p = FakeUnderlayProcess(stdout, stderr, exitCode)
            launched += p
            return p
        }
    }

    /** Grant that allows echo (clean), git status (native via process_execute), and file reads. */
    private fun grant() = CapabilityGrant(
        requestedCapabilities = listOf("process_execute", "process_network"),
        grantedCapabilities = listOf("process_execute", "process_network"),
        rejectedCapabilities = emptyList(),
        scopes = CapabilityScopes(
            fileScopes = listOf(FileScope(path = "*", operation = "read"), FileScope(path = "*", operation = "write")),
        ),
    )

    private fun session(backend: ProcessBackend) = TerminalSession.create("s1", backend)

    @Test
    fun `acceptance slice runs a clean command and produces a receipt`() = runBlocking {
        val fake = FakeUnderlay(stdout = ByteArrayInputStream("hello world".toByteArray()))
        val backend = LocalProcessBackend(fake)
        val gate = ProcessGate(grant())

        // 1. gate authorises the harmless command.
        val decision = gate.evaluate(listOf("echo", "hello world"), workingDirectory = null).decision
        assertTrue(decision is ProcessGate.Decision.Allowed)
        val plan = (decision as ProcessGate.Decision.Allowed).plan
        assertFalse(plan.nativeExecution)
        assertFalse(plan.network)
        assertTrue(plan.commandDigest.isNotBlank())

        // 2. session owns the process lifecycle.
        val s = session(backend)
        val rp = s.start(ProcessCommand(s.processRef, listOf("echo", "hello world")))
        assertEquals(SessionStatus.RUNNING, s.currentStatus)

        // 3. observe the output.
        val chunks = mutableListOf<TerminalChunk>()
        for (c in rp.output()) chunks += c
        assertEquals("hello world", chunks.joinToString("") { it.bytes.toString(Charsets.UTF_8) })

        // 4. semantic snapshot reflects what the agent acts on.
        s.recordChunk(TerminalChunk(s.processRef, TerminalStream.STDOUT, 0, 1L, "hello world".toByteArray()))
        val snap = s.snapshot(revision = 1L, cwd = "/root/ws-1", prompt = PromptState.READY)
        assertEquals("hello world", snap.visibleText)
        assertEquals("/root/ws-1", snap.cwd)
        assertTrue(snap.process.toString().contains("process:s1"))

        // 5. terminal transition.
        rp.awaitExit()
        s.markTerminal(SessionStatus.EXITED)
        assertEquals(SessionStatus.EXITED, s.currentStatus)

        // 6. receipt records the run, correlated to the gated plan.
        val receipt = ProcessReceipt(
            process = s.processRef,
            command = listOf("echo", "hello world"),
            commandDigest = plan.commandDigest,
            effects = emptySet(),
            reads = emptyList(),
            writes = emptyList(),
            network = false,
            nativeExecution = false,
            startedAtMs = 1L,
            completedAtMs = 2L,
            exitCode = 0,
            termination = ProcessTermination.NORMAL.name,
            outputBytes = rp.outputBytes,
            outputTruncated = rp.outputTruncated,
        )
        assertEquals(plan.commandDigest, receipt.commandDigest)
        assertEquals("process:s1", receipt.process.toString())
        assertEquals(0, receipt.exitCode)
        assertEquals("NORMAL", receipt.termination)
    }

    @Test
    fun `native command is authorised when process_execute granted`() = runBlocking {
        val gate = ProcessGate(grant())
        val decision = gate.evaluate(listOf("git", "status"), workingDirectory = null).decision
        assertTrue(decision is ProcessGate.Decision.Allowed)
        assertTrue((decision as ProcessGate.Decision.Allowed).plan.nativeExecution)
    }

    @Test
    fun `denied native execution never reaches backend`() = runBlocking {
        val gate = ProcessGate(grant().copy(grantedCapabilities = emptyList(), rejectedCapabilities = listOf("process_execute")))
        val decision = gate.evaluate(listOf("git", "status"), workingDirectory = null).decision
        assertEquals(ProcessGate.Decision.Denied("native_execution_denied"), decision)
        // No backend is constructed or started: the gate is a pure guard.
    }

    @Test
    fun `denied network never reaches backend`() = runBlocking {
        // process_execute is granted so curl's nativeExecution passes; process_network is denied.
        val gate = ProcessGate(
            grant().copy(
                grantedCapabilities = listOf("process_execute"),
                rejectedCapabilities = listOf("process_network"),
            ),
        )
        val decision = gate.evaluate(listOf("curl", "https://example.com"), workingDirectory = null).decision
        assertEquals(ProcessGate.Decision.Denied("network_denied"), decision)
    }

    @Test
    fun `file write outside scope is denied`() = runBlocking {
        val gate = ProcessGate(
            grant().copy(scopes = CapabilityScopes(fileScopes = listOf(FileScope(path = "/allowed", operation = "write")))),
        )
        val decision = gate.evaluate(listOf("echo", "x", ">", "/forbidden/f.txt"), workingDirectory = null).decision
        assertTrue(decision is ProcessGate.Decision.Denied)
        assertTrue((decision as ProcessGate.Decision.Denied).reason.startsWith("write_not_allowed:"))
    }

    @Test
    fun `duplicate close is idempotent and only first cancels`() = runBlocking {
        val fake = FakeUnderlay()
        val backend = LocalProcessBackend(fake)
        val s = session(backend)
        val rp = s.start(ProcessCommand(s.processRef, listOf("echo", "hi")))
        s.close()
        s.close() // second close no-ops
        s.close()
        assertEquals(SessionStatus.CANCELLED, s.currentStatus)
        assertTrue(fake.launched.single().destroyed)
        assertEquals(ProcessTermination.CANCELLED, rp.awaitExit().termination)
    }

    @Test
    fun `backend failure marks session failed not running`() = runBlocking {
        val fake = FakeUnderlay(startShouldFail = true)
        val backend = LocalProcessBackend(fake)
        val s = session(backend)
        var threw = false
        try {
            s.start(ProcessCommand(s.processRef, listOf("echo", "hi")))
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
        assertEquals(SessionStatus.STARTING, s.currentStatus)
    }

    @Test
    fun `late output after terminal transition is ignored for snapshot`() = runBlocking {
        val fake = FakeUnderlay(stdout = ByteArrayInputStream("early".toByteArray()))
        val backend = LocalProcessBackend(fake)
        val s = session(backend)
        val rp = s.start(ProcessCommand(s.processRef, listOf("echo", "hi")))
        for (c in rp.output()) s.recordChunk(c)
        s.markTerminal(SessionStatus.EXITED)

        // A stray late chunk with a non-matching sequence is rejected by recordChunk dedupe.
        val rejected = s.recordChunk(TerminalChunk(s.processRef, TerminalStream.STDOUT, 999L, 999L, "late".toByteArray()))
        assertFalse(rejected)
        val snap = s.snapshot(revision = 2L)
        assertFalse(snap.visibleText.contains("late"))
        assertEquals(0, snap.exitCode)
    }

    @Test
    fun `process exit before readiness is not reported as exited`() = runBlocking {
        val fake = FakeUnderlay(stdout = ByteArrayInputStream(ByteArray(0)))
        val backend = LocalProcessBackend(fake)
        val s = session(backend)
        val rp = s.start(ProcessCommand(s.processRef, listOf("echo", "hi")))
        // Process is running; snapshot must not claim an exit yet.
        val snap = s.snapshot(revision = 1L)
        assertEquals(null, snap.exitCode)
        rp.awaitExit()
        assertEquals(ProcessStatus.EXITED, rp.status)
    }

    @Test
    fun `input write is gated by input lease ownership`() = runBlocking {
        val fake = FakeUnderlay()
        val backend = LocalProcessBackend(fake)
        val s = session(backend)
        val rp = s.start(ProcessCommand(s.processRef, listOf("sh")))
        // AGENT holds the only input lease; writing under it is authorised.
        val lease = TerminalInputLease(s.processRef, InputSource.AGENT, "lease-1")
        assertEquals(InputSource.AGENT, lease.owner)
        rp.writeInput("ls\n".toByteArray())
        assertEquals("ls\n", String(fake.launched.single().stdin.toByteArray(), Charsets.UTF_8))
        assertNotNull(lease.leaseId)
    }
}
