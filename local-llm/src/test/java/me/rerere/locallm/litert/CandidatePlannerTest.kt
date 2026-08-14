package me.rerere.locallm.litert

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidatePlannerTest {

    private val tool: Tool = Tool(
        name = "write_file",
        description = "writes a file",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string") })
                    put("mode", buildJsonObject { put("type", "string"); put("default", "overwrite") })
                    put("lines", buildJsonObject { put("type", "integer") })
                },
                required = listOf("path", "mode"),
            )
        },
        systemPrompt = { _, _ -> "" },
        needsApproval = { false },
        execute = { args -> listOf(UIMessagePart.Text(args.toString())) },
    )

    private val context = CompilationContext(
        toolCatalog = mapOf("write_file" to tool),
        canonicalAliases = mapOf("wf" to "write_file"),
    )

    private val planner = CandidatePlanner()

    private fun grant(requested: List<String> = listOf("write_file")) =
        CapabilityGrant(requested, requested, emptyList())

    private fun call(toolName: String, args: JsonObject) =
        ActionPlan.ToolCall(toolName, args, grant())

    @Test
    fun primaryAlwaysPresent() {
        val plan = call("write_file", buildJsonObject { put("path", "/tmp/f"); put("mode", "overwrite") })
        val ids = planner.propose(plan, context).map { it.id }
        assertEquals(listOf("candidate-primary"), ids)
    }

    @Test
    fun aliasNormalizedProposedWhenResolving() {
        val plan = call("wf", buildJsonObject { put("path", "/tmp/f") })
        val candidates = planner.propose(plan, context)
        val alias = candidates.firstOrNull { it.source == ShadowCandidate.Source.ALIAS_NORMALIZED }
        assertNotNull(alias)
        assertEquals("write_file", (alias!!.plan as ActionPlan.ToolCall).toolName)
    }

    @Test
    fun defaultFilledProposedWhenMissingRequiredHasDefault() {
        val plan = call("write_file", buildJsonObject { put("path", "/tmp/f") })
        val candidates = planner.propose(plan, context)
        val filled = candidates.firstOrNull { it.source == ShadowCandidate.Source.DEFAULT_FILLED }
        assertNotNull(filled)
        val args = (filled!!.plan as ActionPlan.ToolCall).args
        assertEquals("overwrite", args["mode"]?.toString()?.trim('"'))
    }

    @Test
    fun noDefaultCandidateWhenRequiredMissingWithoutDefault() {
        val plan = call("write_file", buildJsonObject { put("mode", "overwrite") })
        val candidates = planner.propose(plan, context)
        assertTrue(candidates.none { it.source == ShadowCandidate.Source.DEFAULT_FILLED })
    }

    @Test
    fun coercedArgsProposedWhenStringCoercible() {
        val plan = call("write_file", buildJsonObject { put("path", "/tmp/f"); put("mode", "overwrite"); put("lines", "30") })
        val candidates = planner.propose(plan, context)
        val coerced = candidates.firstOrNull { it.source == ShadowCandidate.Source.COERCED_ARGS }
        assertNotNull(coerced)
        val args = (coerced!!.plan as ActionPlan.ToolCall).args
        assertEquals("30", args["lines"]?.toString())
    }

    @Test
    fun noCoerceCandidateWhenNothingCoercible() {
        val plan = call("write_file", buildJsonObject { put("path", "/tmp/f"); put("mode", "overwrite") })
        val candidates = planner.propose(plan, context)
        assertTrue(candidates.none { it.source == ShadowCandidate.Source.COERCED_ARGS })
    }

    @Test
    fun workflowAndProcedureCallsOnlyProposePrimary() {
        val workflow = ActionPlan.WorkflowCall("w1", buildJsonObject {}, grant())
        assertEquals(1, planner.propose(workflow, context).size)
        val proc = ActionPlan.ProcedureCall("p1", buildJsonObject {}, grant())
        assertEquals(1, planner.propose(proc, context).size)
    }
}
