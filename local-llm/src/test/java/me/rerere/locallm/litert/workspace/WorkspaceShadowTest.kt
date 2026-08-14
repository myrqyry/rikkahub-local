package me.rerere.locallm.litert.workspace

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ShadowWorkspaceTest {

    private val realFiles = HashMap<String, ByteArray>()
    private val ref = WorkspaceRef("ws-1", "/root/ws-1")

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

    private fun shadow(): ShadowWorkspace = ShadowWorkspace(ref, backend())

    @Test
    fun `read-through returns real file when not overlaid`() = runBlocking {
        realFiles["a.txt"] = "hello".toByteArray()
        assertEquals("hello", shadow().readText("a.txt"))
    }

    @Test
    fun `write overlays without touching the real workspace`() = runBlocking {
        realFiles["a.txt"] = "hello".toByteArray()
        val s = shadow()
        s.writeText("a.txt", "shadowed")
        assertEquals("shadowed", s.readText("a.txt"))
        assertEquals("hello", String(realFiles["a.txt"]!!, StandardCharsets.UTF_8))
    }

    @Test
    fun `delete becomes a tombstone and is not forwarded to real workspace`() = runBlocking {
        realFiles["a.txt"] = "hello".toByteArray()
        val s = shadow()
        s.delete("a.txt")
        assertNull(s.readText("a.txt"))
        assertFalse(s.exists("a.txt"))
        assertTrue(realFiles.containsKey("a.txt"))
    }

    @Test
    fun `diff is deterministic and sorted`() = runBlocking {
        realFiles["keep.txt"] = "x".toByteArray()
        val s = shadow()
        s.writeText("b.txt", "new")
        s.delete("keep.txt")
        s.writeText("a.txt", "first")
        val diff = s.diff()
        val paths = diff.entries.map { it.path }
        // deleted (keep.txt) sorts before added; adds sorted by path
        assertEquals(listOf("keep.txt", "a.txt", "b.txt"), paths)
        assertEquals(3, diff.entries.size)
    }

    @Test
    fun `apply materializes operations without executing them`() = runBlocking {
        val s = shadow()
        s.writeText("x.txt", "X")
        s.delete("y.txt")
        val (ops, summary) = s.apply()
        assertEquals(2, ops.size)
        assertTrue(ops.any { it.kind == WorkspaceOperationKind.WRITE && it.file!!.path == "x.txt" })
        assertTrue(ops.any { it.kind == WorkspaceOperationKind.DELETE && it.file!!.path == "y.txt" })
        assertTrue(summary.contains("x.txt"))
        assertFalse(realFiles.containsKey("x.txt"))
    }

    // --- F0: real workspace ref integrity ---

    @Test
    fun `passthrough read carries the real workspace ref`() = runBlocking {
        val seen = mutableListOf<WorkspaceFileRef?>()
        val b = WorkspaceBackend { op ->
            seen += op.file
            if (op.kind == WorkspaceOperationKind.READ) WorkspaceResult.Read("data".toByteArray()) else WorkspaceResult.Failed("n/a")
        }
        val s = ShadowWorkspace(ref, b)
        s.read("a.txt")
        assertEquals(ref, seen.single()!!.workspace)
    }

    @Test
    fun `apply operations carry the real workspace ref not blank`() = runBlocking {
        val s = shadow()
        s.writeText("x.txt", "X")
        s.delete("y.txt")
        val (ops, _) = s.apply()
        assertTrue(ops.all { it.file!!.workspace == ref })
        assertTrue(ops.none { it.file!!.workspace.workspaceId.isEmpty() || it.file!!.workspace.root.isEmpty() })
    }

    @Test
    fun `distinct shadow workspaces never bleed refs across each other`() = runBlocking {
        val refA = WorkspaceRef("ws-a", "/a")
        val refB = WorkspaceRef("ws-b", "/b")
        val a = ShadowWorkspace(refA, backend())
        val b = ShadowWorkspace(refB, backend())
        a.writeText("shared.txt", "from-a")
        val (opsA, _) = a.apply()
        val (opsB, _) = b.apply()
        assertTrue(opsA.all { it.file!!.workspace == refA })
        assertEquals(0, opsB.size)
    }

    // --- F0: ADDED vs MODIFIED base-presence distinction ---

    @Test
    fun `overlay of a base file is MODIFIED not ADDED`() = runBlocking {
        realFiles["base.txt"] = "original".toByteArray()
        val s = shadow()
        s.writeText("base.txt", "edited")
        val kinds = s.diff().entries.associate { it.path to it.kind }
        assertEquals(DiffKind.MODIFIED, kinds["base.txt"])
    }

    @Test
    fun `overlay of a new file is ADDED`() = runBlocking {
        val s = shadow()
        s.writeText("new.txt", "brand new")
        val kinds = s.diff().entries.associate { it.path to it.kind }
        assertEquals(DiffKind.ADDED, kinds["new.txt"])
    }

    @Test
    fun `delete of a base file is DELETED and never misclassified`() = runBlocking {
        realFiles["base.txt"] = "x".toByteArray()
        val s = shadow()
        s.delete("base.txt")
        val kinds = s.diff().entries.associate { it.path to it.kind }
        assertEquals(DiffKind.DELETED, kinds["base.txt"])
    }

    @Test
    fun `write then delete of a base file collapses to DELETED not MODIFIED`() = runBlocking {
        realFiles["base.txt"] = "x".toByteArray()
        val s = shadow()
        s.writeText("base.txt", "y")
        s.delete("base.txt")
        val kinds = s.diff().entries.associate { it.path to it.kind }
        assertEquals(DiffKind.DELETED, kinds["base.txt"])
    }

    @Test
    fun `added and modified files sort deterministically with deleted first`() = runBlocking {
        realFiles["mod.txt"] = "x".toByteArray()
        val s = shadow()
        s.writeText("add.txt", "new")
        s.writeText("mod.txt", "edited")
        val paths = s.diff().entries.map { it.path }
        assertEquals(listOf("add.txt", "mod.txt"), paths)
        assertNotEquals(
            s.diff().entries.associate { it.path to it.kind },
            mapOf("add.txt" to DiffKind.MODIFIED, "mod.txt" to DiffKind.ADDED),
        )
    }
}

class SimulatedShellExecutorTest {

    private val ref = WorkspaceRef("ws-1", "/root/ws-1")

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
        val shadow = ShadowWorkspace(ref, backend)
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
    fun `git status is native and not network`() {
        val fx = CommandEffectAnalyzer.analyze(listOf("git", "status"))
        assertTrue(fx.nativeExecution)
        assertFalse(fx.network)
    }

    // --- F0: network-capable command preflight ---

    @Test
    fun `git remote fetch is native and network`() {
        val fx = CommandEffectAnalyzer.analyze(listOf("git", "fetch", "origin"))
        assertTrue(fx.nativeExecution)
        assertTrue(fx.network)
    }

    @Test
    fun `git clone with url is native and network`() {
        val fx = CommandEffectAnalyzer.analyze(listOf("git", "clone", "git@github.com:org/repo.git"))
        assertTrue(fx.nativeExecution)
        assertTrue(fx.network)
    }

    @Test
    fun `ssh to a host is native and network`() {
        val fx = CommandEffectAnalyzer.analyze(listOf("ssh", "user@host", "ls"))
        assertTrue(fx.nativeExecution)
        assertTrue(fx.network)
    }

    @Test
    fun `git diff is native but not network`() {
        val fx = CommandEffectAnalyzer.analyze(listOf("git", "diff", "HEAD~1"))
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
