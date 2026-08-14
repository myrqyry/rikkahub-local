package me.rerere.locallm.litert.terminal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalProcessBackendTest {

    /** [InputStream] that returns at most [chunkSize] bytes per read — simulates slow/segmented output. */
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

        override fun writeStdin(bytes: ByteArray) {
            stdin.write(bytes)
        }

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
    ) : ProcessUnderlay {
        val startedCommands = mutableListOf<List<String>>()
        val launched = mutableListOf<FakeUnderlayProcess>()

        override fun start(command: List<String>, env: Map<String, String>, dir: String?): ProcessUnderlay.UnderlayProcess {
            startedCommands += command
            val p = FakeUnderlayProcess(stdout, stderr, exitCode)
            launched += p
            return p
        }
    }

    private fun command(cmd: List<String> = listOf("echo", "hi")) =
        ProcessCommand(ProcessRef("p1"), cmd)

    @Test
    fun `start returns a running process`() = runBlocking {
        val fake = FakeUnderlay()
        val backend = LocalProcessBackend(fake)
        val rp = backend.start(command())
        assertNotNull(rp)
        assertEquals(ProcessStatus.RUNNING, rp.status)
        assertEquals(listOf(listOf("echo", "hi")), fake.startedCommands)
        assertEquals(1, fake.launched.size)
    }

    @Test
    fun `output is streamed as terminal chunks with increasing sequence`() = runBlocking {
        val fake = FakeUnderlay(stdout = ChunkedInputStream("hello world".toByteArray(), 4))
        val backend = LocalProcessBackend(fake)
        val rp = backend.start(command())
        val chunks = mutableListOf<TerminalChunk>()
        for (c in rp.output()) chunks += c
        assertEquals(listOf(0L, 1L, 2L), chunks.map { it.sequence })
        assertTrue(chunks.all { it.stream == TerminalStream.STDOUT })
        assertEquals("hell", String(chunks[0].bytes, StandardCharsets.UTF_8))
        assertEquals("hello world", chunks.joinToString("") { it.bytes.toString(Charsets.UTF_8) })
        assertEquals(ProcessRef("p1"), chunks[0].process)
    }

    @Test
    fun `stderr is streamed with STDERR stream`() = runBlocking {
        val fake = FakeUnderlay(stderr = ByteArrayInputStream("err".toByteArray()))
        val backend = LocalProcessBackend(fake)
        val rp = backend.start(command())
        val chunks = mutableListOf<TerminalChunk>()
        for (c in rp.output()) chunks += c
        assertEquals(1, chunks.size)
        assertEquals(TerminalStream.STDERR, chunks[0].stream)
        assertEquals("err", String(chunks[0].bytes, StandardCharsets.UTF_8))
    }

    @Test
    fun `awaitExit returns NORMAL with exit code`() = runBlocking {
        val fake = FakeUnderlay(stdout = ByteArrayInputStream("done".toByteArray()), exitCode = 7)
        val backend = LocalProcessBackend(fake)
        val rp = backend.start(command())
        for (c in rp.output()) { /* drain */ }
        val completion = rp.awaitExit()
        assertEquals(7, completion.exitCode)
        assertEquals(ProcessTermination.NORMAL, completion.termination)
        assertEquals(ProcessStatus.EXITED, rp.status)
    }

    @Test
    fun `cancel destroys underlay and returns CANCELLED`() = runBlocking {
        val fake = FakeUnderlay()
        val backend = LocalProcessBackend(fake)
        val rp = backend.start(command())
        rp.cancel()
        assertTrue(fake.launched.single().destroyed)
        val completion = rp.awaitExit()
        assertEquals(ProcessTermination.CANCELLED, completion.termination)
        assertEquals(ProcessStatus.CANCELLED, rp.status)
    }

    @Test
    fun `writeInput writes to underlay stdin`() = runBlocking {
        val fake = FakeUnderlay()
        val backend = LocalProcessBackend(fake)
        val rp = backend.start(command())
        rp.writeInput("hello".toByteArray())
        assertEquals("hello", String(fake.launched.single().stdin.toByteArray(), StandardCharsets.UTF_8))
    }

    @Test
    fun `output cap truncates and sets outputTruncated`() = runBlocking {
        val fake = FakeUnderlay(stdout = ByteArrayInputStream("0123456789abc".toByteArray())) // 13 bytes
        val backend = LocalProcessBackend(fake, maxOutputBytes = 10)
        val rp = backend.start(command())
        var total = 0L
        for (c in rp.output()) total += c.bytes.size
        rp.awaitExit()
        assertTrue(rp.outputTruncated)
        assertTrue(total <= 10)
        assertEquals(10L, rp.outputBytes)
    }
}
