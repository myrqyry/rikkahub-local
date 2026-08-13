package me.rerere.locallm.litert.zero

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroProcedureEngineTest {

    private val engine = ZeroProcedureEngine()

    private fun echoTool(): Tool = Tool(
        name = "echo",
        description = "echo back its args as a JSON object",
        parameters = { null },
        execute = { args -> listOf(UIMessagePart.Text(args.toString())) },
    )

    private fun staticTool(name: String, output: String): Tool = Tool(
        name = name,
        description = "returns a fixed output",
        parameters = { null },
        execute = { listOf(UIMessagePart.Text(output)) },
    )

    private fun proc(vararg steps: ZeroStep) =
        ZeroProcedure(id = "p1", steps = steps.toList())

    private fun step(
        stepId: String,
        tool: String,
        args: kotlinx.serialization.json.JsonObject = buildJsonObject { },
        timeoutSeconds: Int = 60,
    ) = ZeroStep(stepId, tool, args, timeoutSeconds)

    private val catalog = mapOf(
        "echo" to echoTool(),
        "static_json" to staticTool("static_json", """{"result": 42, "name": "rikka"}"""),
        "static_plain" to staticTool("static_plain", "hello world"),
    )

    @Test
    fun `valid procedure compiles to topological order`() = runBlocking {
        val p = proc(
            step("a", "static_plain"),
            step("b", "echo", buildJsonObject { put("msg", "{{a}}") }),
        )
        val compiled = engine.compile(p, catalog)
        assertTrue("expected Valid, got $compiled", compiled is ZeroCompilationResult.Valid)
        assertEquals(listOf("a", "b"), (compiled as ZeroCompilationResult.Valid).order)
    }

    @Test
    fun `single independent step runs and captures output`() = runBlocking {
        val p = proc(step("a", "static_json"))
        val result = engine.execute(p, catalog)
        assertTrue("expected success, got $result", result.success)
        assertEquals(1, result.stepResults.size)
        assertTrue(result.stepResults[0].success)
        // Output collapses to the parsed JSON object (key order = source order).
        assertEquals(
            """{"result":42,"name":"rikka"}""",
            result.outputs["a"]?.toString(),
        )
    }

    @Test
    fun `template reference substitutes whole output`() = runBlocking {
        val p = proc(
            step("a", "static_plain"),
            step("b", "echo", buildJsonObject { put("msg", "said: {{a}}") }),
        )
        val result = engine.execute(p, catalog)
        assertTrue("expected success, got $result", result.success)
        assertEquals(2, result.stepResults.size)
        // echo returns `{"msg": "said: hello world"}` as text → parsed back to JSON object.
        val bOutput = result.outputs["b"]!!
        assertEquals("said: hello world", (bOutput as kotlinx.serialization.json.JsonObject)["msg"]!!.jsonPrimitive.content)
    }

    @Test
    fun `template path reference navigates prior json output`() = runBlocking {
        val p = proc(
            step("a", "static_json"),
            step("b", "echo", buildJsonObject { put("result", "{{a.result}}"); put("name", "{{a.name}}") }),
        )
        val result = engine.execute(p, catalog)
        assertTrue("expected success, got $result", result.success)
        val bOutput = result.outputs["b"]!! as kotlinx.serialization.json.JsonObject
        assertEquals("42", bOutput["result"]!!.jsonPrimitive.content)
        assertEquals("rikka", bOutput["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `duplicate step id rejected`() = runBlocking {
        val p = proc(step("a", "static_plain"), step("a", "static_plain"))
        val compiled = engine.compile(p, catalog)
        assertTrue(compiled is ZeroCompilationResult.Invalid)
        val diag = (compiled as ZeroCompilationResult.Invalid).diagnostics
        assertTrue(diag.any { it.code == "ZERO_DUP_STEP" })
    }

    @Test
    fun `unknown tool rejected`() = runBlocking {
        val p = proc(step("a", "no_such_tool"))
        val compiled = engine.compile(p, catalog)
        assertTrue(compiled is ZeroCompilationResult.Invalid)
        val diag = (compiled as ZeroCompilationResult.Invalid).diagnostics
        assertTrue(diag.any { it.code == "ZERO_UNKNOWN_TOOL" })
    }

    @Test
    fun `dangling reference rejected`() = runBlocking {
        val p = proc(step("a", "echo", buildJsonObject { put("msg", "{{ghost}}") }))
        val compiled = engine.compile(p, catalog)
        assertTrue(compiled is ZeroCompilationResult.Invalid)
        val diag = (compiled as ZeroCompilationResult.Invalid).diagnostics
        assertTrue(diag.any { it.code == "ZERO_DANGLING_REF" })
    }

    @Test
    fun `self reference rejected`() = runBlocking {
        val p = proc(step("a", "echo", buildJsonObject { put("msg", "{{a}}") }))
        val compiled = engine.compile(p, catalog)
        assertTrue(compiled is ZeroCompilationResult.Invalid)
        val diag = (compiled as ZeroCompilationResult.Invalid).diagnostics
        assertTrue(diag.any { it.code == "ZERO_SELF_REF" })
    }

    @Test
    fun `reference cycle rejected`() = runBlocking {
        val p = proc(
            step("a", "echo", buildJsonObject { put("msg", "{{b}}") }),
            step("b", "echo", buildJsonObject { put("msg", "{{a}}") }),
        )
        val compiled = engine.compile(p, catalog)
        assertTrue(compiled is ZeroCompilationResult.Invalid)
        val diag = (compiled as ZeroCompilationResult.Invalid).diagnostics
        assertTrue(diag.any { it.code == "ZERO_CYCLE" })
    }

    @Test
    fun `failFast stops after first failed step`() = runBlocking {
        val boom = Tool(
            name = "boom",
            description = "always throws",
            parameters = { null },
            execute = { throw IllegalStateException("kaboom") },
        )
        val localCatalog = catalog + ("boom" to boom)
        val p = proc(
            step("a", "boom"),
            step("b", "static_plain"),
        )
        val result = engine.execute(p, localCatalog)
        assertFalse(result.success)
        assertEquals("step 'a': IllegalStateException: kaboom", result.error)
        // failFast=true: step b is skipped, not executed.
        assertEquals(2, result.stepResults.size)
        assertFalse(result.stepResults[1].success)
        assertTrue(result.stepResults[1].error!!.startsWith("skipped:"))
    }

    @Test
    fun `failFast false continues past failed step`() = runBlocking {
        val boom = Tool(
            name = "boom",
            description = "always throws",
            parameters = { null },
            execute = { throw IllegalStateException("kaboom") },
        )
        val localCatalog = catalog + ("boom" to boom)
        val p = ZeroProcedure(
            id = "p2",
            steps = listOf(
                step("a", "boom"),
                step("b", "static_plain"),
            ),
            failFast = false,
        )
        val result = engine.execute(p, localCatalog)
        assertFalse(result.success) // overall failure still reported
        assertEquals(2, result.stepResults.size)
        assertFalse(result.stepResults[0].success)
        assertTrue(result.stepResults[1].success)
    }

    @Test
    fun `timeout exceeds per-step limit`() = runBlocking {
        val slow = Tool(
            name = "slow",
            description = "takes forever",
            parameters = { null },
            execute = { kotlinx.coroutines.delay(10_000); listOf(UIMessagePart.Text("done")) },
        )
        val localCatalog = catalog + ("slow" to slow)
        val p = proc(step("a", "slow", timeoutSeconds = 1))
        val result = engine.execute(p, localCatalog)
        assertFalse(result.success)
        assertTrue(result.error!!.contains("exceeded 1s"))
    }

    @Test
    fun `unresolvable template degrades to literal marker`() = runBlocking {
        // 'ghost' never executes, but the ref only exists in this step's args → compile
        // would reject it. To exercise runtime degradation, reference a step that compiles
        // away? Steps never compile away, so instead reference a step id that only appears
        // in the runtime map after failure: a dangling compile passes only if target exists
        // in the procedure. So simulate by a valid compile with a step whose output is empty.
        val p = proc(
            step("a", "static_json"),
            step("b", "echo", buildJsonObject { put("path", "{{a.missing.path}}") }),
        )
        val compiled = engine.compile(p, catalog)
        assertTrue("expected Valid, got $compiled", compiled is ZeroCompilationResult.Valid)
        val result = engine.execute(p, catalog)
        assertTrue(result.success)
        val bOutput = result.outputs["b"]!! as kotlinx.serialization.json.JsonObject
        assertEquals("{{a.missing.path:missing_path}}", bOutput["path"]!!.jsonPrimitive.content)
    }
}
