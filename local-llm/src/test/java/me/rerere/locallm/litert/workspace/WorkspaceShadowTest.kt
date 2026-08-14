package me.rerere.locallm.litert.workspace

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ShadowWorkspaceTest {

    private val realFiles = HashMap<String, ByteArray>()

    private fun backend(): WorkspaceBackend = WorkspaceBackend { op ->
        when (op.kind) {
            WorkspaceOperationKind.READ -> {
                val path = op.file!!.path
                val content = realFiles[path]
                if (content == null) WorkspaceResult.Failed("no such file") else WorkspaceResult.Read(content)
            }

            WorkspaceOperationKind.WRITE -> {
                realFiles[op.file!!.path] = op.content ?: ByteArray(0)
                WorkspaceResult.Write
            }

            WorkspaceOperationKind.DELETE -> {
                realFiles.remove(op.file!!.path)
                WorkspaceResult.Delete
            }

            else -> WorkspaceResult.Failed("unimplemented")
        }
    }

    @Test
    fun `read-through returns real file when not overlaid`() = runBlocking {
        realFiles["a.txt"] = "hello".toByteArray()
        val shadow = ShadowWorkspace(backend())
        assertEquals("hello", shadow.readText("a.txt"))
    }

    @Test
    fun `write overlays without touching the real workspace`() = runBlocking {
        realFiles["a.txt"] = "hello".toByteArray()
        val shadow = ShadowWorkspace(backend())
        shadow.writeText("a.txt", "shadowed")
        assertEquals("shadowed", shadow.readText("a.txt"))
        assertEquals("hello", String(realFiles["a.txt"]!!, StandardCharsets.UTF_8))
    }

    @Test
    fun `delete becomes a tombstone and is not forwarded to real workspace`() = runBlocking {
        realFiles["a.txt"] = "hello".toByteArray()
        val shadow = ShadowWorkspace(backend())
        shadow.delete("a.txt")
        assertNull(shadow.readText("a.txt"))
        assertFalse(shadow.exists("a.txt"))
        assertTrue(realFiles.containsKey("a.txt"))
    }

    @Test
    fun `diff is deterministic and sorted`() = runBlocking {
        realFiles["keep.txt"] = "x".toByteArray()
        val shadow = ShadowWorkspace(backend())
        shadow.writeText("b.txt", "new")
        shadow.delete("keep.txt")
        shadow.writeText("a.txt", "first")
        val diff = shadow.diff()
        val paths = diff.entries.map { it.path }
        // deleted (keep.txt) sorts before added; adds sorted by path
        assertEquals(listOf("keep.txt", "a.txt", "b.txt"), paths)
        assertEquals(3, diff.entries.size)
    }

    @Test
    fun `apply materializes operations without executing them`() = runBlocking {
        val shadow = ShadowWorkspace(backend())
        shadow.writeText("x.txt", "X")
        shadow.delete("y.txt")
        val (ops, summary) = shadow.apply()
        assertEquals(2, ops.size)
        assertTrue(ops.any { it.kind == WorkspaceOperationKind.WRITE && it.file!!.path == "x.txt" })
        assertTrue(ops.any { it.kind == WorkspaceOperationKind.DELETE && it.file!!.path == "y.txt" })
        assertTrue(summary.contains("x.txt"))
        assertFalse(realFiles.containsKey("x.txt"))
    }
}

class SimulatedShellExecutorTest {

    private fun workspace(): ShadowWorkspace {
        val real = HashMap<String, ByteArray>()
        val backend = WorkspaceBackend { op ->
            when (op.kind) {
                WorkspaceOperationKind.READ -> {
                    val c = real[op.file!!.path]
                    if (c == null) WorkspaceResult.Failed("missing") else WorkspaceResult.Read(c)
                }

                WorkspaceOperationKind.WRITE -> {
                    real[op.file!!.path] = op.content ?: ByteArray(0)
                    WorkspaceResult.Write
                }

                WorkspaceOperationKind.DELETE -> {
                    real.remove(op.file!!.path)
                    WorkspaceResult.Delete
                }

                else -> WorkspaceResult.Failed("unimplemented")
            }
        }
        val shadow = ShadowWorkspace(backend)
        runBlocking { shadow.writeText("note.txt", "alpha beta gamma") }
        return shadow
    }

    private val shell: SimulatedShellExecutor by lazy { SimulatedShellExecutor(workspace()) }

    @Test
    fun `cat reads through the shadow`() = runBlocking {
        val r = shell.run(listOf("cat", "note.txt"))
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("alpha"))
    }

    @Test
    fun `echo redirect writes into shadow overlay`() = runBlocking {
        val r = shell.run(listOf("echo", "new line", ">", "out.txt"))
        assertEquals(0, r.exitCode)
        val cat = shell.run(listOf("cat", "out.txt"))
        assertTrue(cat.stdout.contains("new line"))
    }

    @Test
    fun `grep finds matches and reports no match with exit 1`() = runBlocking {
        val hit = shell.run(listOf("grep", "beta", "note.txt"))
        assertEquals(0, hit.exitCode)
        assertTrue(hit.stdout.contains("beta"))
        val miss = shell.run(listOf("grep", "zzz", "note.txt"))
        assertEquals(1, miss.exitCode)
    }

    @Test
    fun `mv deletes source and writes destination`() = runBlocking {
        val r = shell.run(listOf("mv", "note.txt", "renamed.txt"))
        assertEquals(0, r.exitCode)
        val cat = shell.run(listOf("cat", "renamed.txt"))
        assertEquals(0, cat.exitCode)
        assertTrue(cat.stdout.contains("alpha"))
        assertEquals(1, shell.run(listOf("cat", "note.txt")).exitCode)
    }

    @Test
    fun `unsupported native command is refused with 126`() = runBlocking {
        val r = shell.run(listOf("git", "status"))
        assertEquals(126, r.exitCode)
    }

    @Test
    fun `sed transforms content`() = runBlocking {
        val r = shell.run(listOf("sed", "s/beta/delta/", "note.txt"))
        assertEquals(0, r.exitCode)
        assertTrue(shell.run(listOf("cat", "note.txt")).stdout.contains("delta"))
    }
}

class CommandEffectAnalyzerTest {

    @Test
    fun `cat flags read and not native`() {
        val fx = CommandEffectAnalyzer.analyze(listOf("cat", "a.txt"))
        assertTrue(fx.reads.contains("a.txt"))
        assertFalse(fx.nativeExecution)
    }

    @Test
    fun `redirect flags write`() {
        val fx = CommandEffectAnalyzer.analyze(listOf("echo", "x", ">", "out.txt"))
        assertTrue(fx.writes.contains("out.txt"))
    }

    @Test
    fun `git is native but not network`() {
        val fx = CommandEffectAnalyzer.analyze(listOf("git", "status"))
        assertTrue(fx.nativeExecution)
        assertFalse(fx.network)
    }

    @Test
    fun `curl is native and network`() {
        val fx = CommandEffectAnalyzer.analyze(listOf("curl", "https://example.com"))
        assertTrue(fx.nativeExecution)
        assertTrue(fx.network)
    }

    @Test
    fun `empty command is clean`() {
        val fx = CommandEffectAnalyzer.analyze(emptyList())
        assertTrue(fx.isClean)
    }
}
