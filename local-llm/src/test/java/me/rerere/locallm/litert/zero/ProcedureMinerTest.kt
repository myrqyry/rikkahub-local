package me.rerere.locallm.litert.zero

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcedureMinerTest {

    private fun exec(
        tool: String,
        atMs: Long = 0L,
    ) = ToolExecution(toolName = tool, atMs = atMs)

    @Test
    fun `empty history mines nothing`() {
        val result = ProcedureMiner().mine(emptyList())
        assertTrue(result.mined.isEmpty())
        assertEquals(0, result.totalExecutions)
    }

    @Test
    fun `history below minSteps mines nothing`() {
        val result = ProcedureMiner().mine(listOf(exec("a"), exec("b")))
        assertTrue(result.mined.isEmpty())
        assertEquals(2, result.totalExecutions)
    }

    @Test
    fun `single recurring sequence is mined with support count`() {
        val history = listOf(
            exec("a", 100), exec("b", 101), exec("c", 102),
            exec("a", 200), exec("b", 201), exec("c", 202),
        )
        val result = ProcedureMiner().mine(history)
        assertEquals(1, result.mined.size)
        val mined = result.mined.single()
        assertEquals(2, mined.support)
        assertEquals(3, mined.windowLength)
        assertEquals(listOf("a", "b", "c"), mined.procedure.steps.map { it.tool })
        assertEquals(100, mined.firstSeenAtMs)
        assertEquals(202, mined.lastSeenAtMs)
        assertEquals("mined_a_b_c", mined.procedure.id)
    }

    @Test
    fun `longer frequent sequence prunes its contained subsequences`() {
        // a,b,c,d appears twice; its 3-length sub-windows (a,b,c) and (b,c,d) also appear twice.
        val history = listOf(
            exec("a"), exec("b"), exec("c"), exec("d"),
            exec("a"), exec("b"), exec("c"), exec("d"),
        )
        val result = ProcedureMiner().mine(history)
        assertEquals(1, result.mined.size)
        val mined = result.mined.single()
        assertEquals(4, mined.windowLength)
        assertEquals(listOf("a", "b", "c", "d"), mined.procedure.steps.map { it.tool })
    }

    @Test
    fun `sequence below minSupport is not mined`() {
        // a,b,c appears only once (the trailing window), so below minSupport=2.
        val history = listOf(
            exec("a"), exec("b"), exec("c"),
            exec("x"), exec("y"), exec("z"),
        )
        val result = ProcedureMiner().mine(history)
        assertTrue(result.mined.isEmpty())
    }

    @Test
    fun `mined procedure compiles and executes via ZeroProcedureEngine`() = kotlinx.coroutines.runBlocking {
        val history = listOf(
            exec("echo"), exec("echo"), exec("echo"),
            exec("echo"), exec("echo"), exec("echo"),
        )
        val result = ProcedureMiner().mine(history)
        val mined = result.mined.single()
        val catalog = mapOf(
            "echo" to me.rerere.ai.core.Tool(
                name = "echo",
                description = "echo",
                parameters = { null },
                execute = { args -> listOf(me.rerere.ai.ui.UIMessagePart.Text(args.toString())) },
            ),
        )
        val compiled = ZeroProcedureEngine().compile(mined.procedure, catalog)
        assertTrue("expected Valid, got $compiled", compiled is ZeroCompilationResult.Valid)
        val executed = ZeroProcedureEngine().execute(mined.procedure, catalog)
        assertTrue("expected success, got ${executed.error}", executed.success)
    }

    @Test
    fun `multiple sequences rank by support then length then id`() {
        // (a,b,c) occurs 3x and (d,e,f,g) occurs 2x, in fully separate blocks so
        // neither window nests inside the other.
        val history = listOf(
            exec("a"), exec("b"), exec("c"), exec("n1"),
            exec("a"), exec("b"), exec("c"), exec("n2"),
            exec("a"), exec("b"), exec("c"), exec("n3"),
            exec("d"), exec("e"), exec("f"), exec("g"),
            exec("d"), exec("e"), exec("f"), exec("g"),
        )
        val result = ProcedureMiner().mine(history)
        assertEquals(2, result.mined.size)
        // a,b,c has support 3 > d,e,f,g support 2, so it ranks first despite shorter length.
        val first = result.mined[0]
        val second = result.mined[1]
        assertEquals(listOf("a", "b", "c"), first.procedure.steps.map { it.tool })
        assertEquals(3, first.support)
        assertEquals(listOf("d", "e", "f", "g"), second.procedure.steps.map { it.tool })
        assertEquals(2, second.support)
    }

    @Test
    fun `mined steps carry the args of the first occurrence`() {
        val args = buildJsonObject { put("path", "/tmp/f.txt") }
        val history = listOf(
            exec("a"), ToolExecution("b", args, 1L), exec("c"),
            exec("a"), ToolExecution("b", buildJsonObject { put("path", "/other") }, 2L), exec("c"),
        )
        val result = ProcedureMiner().mine(history)
        val stepB = result.mined.single().procedure.steps[1]
        assertEquals("b", stepB.tool)
        assertEquals("/tmp/f.txt", stepB.args["path"]?.toString()?.replace("\"", ""))
    }

    @Test
    fun `cache round-trips a procedure`() = kotlinx.coroutines.runBlocking {
        val cache = InMemoryProcedureCache()
        val p = ZeroProcedure(id = "p1", steps = listOf(ZeroStep("s0", "echo")))
        cache.put(p)
        assertEquals(p, cache.get("p1"))
        assertNull(cache.get("nope"))
        assertEquals(listOf(p), cache.all())
    }

    private class InMemoryProcedureCache : ProcedureCache {
        private val store = LinkedHashMap<String, ZeroProcedure>()
        override suspend fun put(procedure: ZeroProcedure) {
            store[procedure.id] = procedure
        }

        override suspend fun get(id: String): ZeroProcedure? = store[id]

        override suspend fun all(): List<ZeroProcedure> = store.values.toList()
    }
}
