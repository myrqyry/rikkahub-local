package me.rerere.locallm.litert

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultActionPlanCompilerTest {

    private fun tool(
        name: String,
        properties: kotlinx.serialization.json.JsonObject = buildJsonObject { },
        required: List<String> = emptyList(),
    ) = Tool(
        name = name,
        description = "test",
        parameters = { InputSchema.Obj(properties, required) },
        execute = { listOf(UIMessagePart.Text("ok")) },
    )

    private fun prop(type: String, default: JsonPrimitive? = null) = buildJsonObject {
        put("type", type)
        put("description", "arg")
        default?.let { put("default", it) }
    }

    private val context = CompilationContext(
        toolCatalog = mapOf(
            "write_file" to tool(
                name = "write_file",
                properties = buildJsonObject {
                    put("path", prop("string"))
                    put("text", prop("string"))
                    put("overwrite", prop("boolean", JsonPrimitive(true)))
                },
                required = listOf("path", "text"),
            ),
            "count" to tool(
                name = "count",
                properties = buildJsonObject { put("limit", prop("integer", JsonPrimitive(10))) },
                required = listOf("limit"),
            ),
        ),
        canonicalAliases = mapOf("write_f" to "write_file"),
    )

    private val compiler = DefaultActionPlanCompiler()

    private fun toolCall(
        toolName: String,
        args: kotlinx.serialization.json.JsonObject = buildJsonObject { },
    ) = ActionPlan.ToolCall(
        toolName = toolName,
        args = args,
        grant = CapabilityGrant(emptyList(), emptyList(), emptyList()),
    )

    @Test
    fun `valid plan compiles to Valid`() = runBlocking {
        val plan = toolCall("write_file", buildJsonObject {
            put("path", "/a.txt")
            put("text", "hi")
        })
        val result = compiler.compile(plan, context)
        assertTrue(result is CompilationResult.Valid)
        assertEquals(plan, (result as CompilationResult.Valid).plan)
    }

    @Test
    fun `unknown tool compiles to Invalid with hint`() = runBlocking {
        val result = compiler.compile(toolCall("nope"), context)
        assertTrue(result is CompilationResult.Invalid)
        val invalid = result as CompilationResult.Invalid
        assertEquals("PLAN_TOOL_001", invalid.diagnostics.first().code)
        assertTrue(invalid.repairHints.first().suggestion.contains("write_file"))
    }

    @Test
    fun `deprecated alias is repaired to canonical tool`() = runBlocking {
        val result = compiler.compile(toolCall("write_f", buildJsonObject {
            put("path", "/a.txt")
            put("text", "hi")
        }), context)
        assertTrue(result is CompilationResult.Repaired)
        val repaired = result as CompilationResult.Repaired
        val repairedPlan = repaired.repairedPlan as ActionPlan.ToolCall
        assertEquals("write_file", repairedPlan.toolName)
        assertTrue(repaired.repairs.any { it.code == "PLAN_TOOL_001" })
    }

    @Test
    fun `missing required arg with schema default is filled`() = runBlocking {
        val result = compiler.compile(toolCall("count"), context)
        assertTrue(result is CompilationResult.Repaired)
        val repaired = result as CompilationResult.Repaired
        val repairedPlan = repaired.repairedPlan as ActionPlan.ToolCall
        assertEquals(JsonPrimitive(10), repairedPlan.args["limit"])
        assertTrue(repaired.repairs.any { it.code == "PLAN_ARG_002" })
    }

    @Test
    fun `missing required arg without default is Invalid`() = runBlocking {
        val result = compiler.compile(toolCall("write_file", buildJsonObject {
            put("path", "/a.txt")
        }), context)
        assertTrue(result is CompilationResult.Invalid)
        val invalid = result as CompilationResult.Invalid
        assertTrue(invalid.diagnostics.any { it.code == "PLAN_ARG_001" && it.message.contains("text") })
    }

    @Test
    fun `string integer is coerced losslessly`() = runBlocking {
        val result = compiler.compile(toolCall("count", buildJsonObject {
            put("limit", "30")
        }), context)
        assertTrue(result is CompilationResult.Repaired)
        val repaired = result as CompilationResult.Repaired
        val repairedPlan = repaired.repairedPlan as ActionPlan.ToolCall
        assertEquals(JsonPrimitive(30), repairedPlan.args["limit"])
        assertTrue(repaired.repairs.any { it.code == "PLAN_ARG_003" })
    }

    @Test
    fun `undeclared argument is dropped`() = runBlocking {
        val result = compiler.compile(toolCall("count", buildJsonObject {
            put("limit", 5L)
            put("bogus", "x")
        }), context)
        assertTrue(result is CompilationResult.Repaired)
        val repaired = result as CompilationResult.Repaired
        val repairedPlan = repaired.repairedPlan as ActionPlan.ToolCall
        assertEquals(false, repairedPlan.args.containsKey("bogus"))
        assertTrue(repaired.repairs.any { it.code == "PLAN_ARG_004" })
    }

    @Test
    fun `blank workflow id is Invalid`() = runBlocking {
        val plan = ActionPlan.WorkflowCall(
            workflowId = "  ",
            inputs = buildJsonObject { },
            grant = CapabilityGrant(emptyList(), emptyList(), emptyList()),
        )
        val result = compiler.compile(plan, context)
        assertTrue(result is CompilationResult.Invalid)
        assertEquals("PLAN_WFLOW_001", (result as CompilationResult.Invalid).diagnostics.first().code)
    }

    @Test
    fun `valid workflow compiles to Valid`() = runBlocking {
        val plan = ActionPlan.WorkflowCall(
            workflowId = "wf-1",
            inputs = buildJsonObject { },
            grant = CapabilityGrant(emptyList(), emptyList(), emptyList()),
        )
        val result = compiler.compile(plan, context)
        assertTrue(result is CompilationResult.Valid)
    }

    @Test
    fun `blank procedure id is Invalid`() = runBlocking {
        val plan = ActionPlan.ProcedureCall(
            procedureId = "  ",
            inputs = buildJsonObject { },
            grant = CapabilityGrant(emptyList(), emptyList(), emptyList()),
        )
        val result = compiler.compile(plan, context)
        assertTrue(result is CompilationResult.Invalid)
        assertEquals("PLAN_PROC_001", (result as CompilationResult.Invalid).diagnostics.first().code)
    }

    @Test
    fun `valid procedure compiles to Valid`() = runBlocking {
        val plan = ActionPlan.ProcedureCall(
            procedureId = "p-1",
            inputs = buildJsonObject { },
            grant = CapabilityGrant(emptyList(), emptyList(), emptyList()),
        )
        val result = compiler.compile(plan, context)
        assertTrue(result is CompilationResult.Valid)
    }
}
