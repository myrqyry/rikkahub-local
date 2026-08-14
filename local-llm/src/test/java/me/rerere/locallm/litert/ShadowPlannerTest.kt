package me.rerere.locallm.litert

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowPlannerTest {

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

    private fun grant(requested: List<String> = listOf("write_file")) =
        CapabilityGrant(requested, requested, emptyList())

    @Test
    fun validPrimaryIsSelected() = runBlocking {
        val plan = ActionPlan.ToolCall("write_file", buildJsonObject { put("path", "/tmp/f"); put("mode", "overwrite") }, grant())
        val selection = ShadowPlanner().select(plan, context)
        assertTrue(selection is ShadowPlanner.Selection.Selected)
        assertEquals(plan, (selection as ShadowPlanner.Selection.Selected).plan)
    }

    @Test
    fun repairedVariantWinsOverInvalidPrimary() = runBlocking {
        // Primary references a deprecated alias (invalid as-is); the alias-normalized
        // variant resolves to a real tool and wins.
        val plan = ActionPlan.ToolCall("wf", buildJsonObject { put("path", "/tmp/f"); put("mode", "overwrite") }, grant(requested = listOf("wf")))
        val selection = ShadowPlanner().select(plan, context)
        assertTrue(selection is ShadowPlanner.Selection.Selected)
        val selected = (selection as ShadowPlanner.Selection.Selected)
        assertEquals("write_file", (selected.plan as ActionPlan.ToolCall).toolName)
    }

    @Test
    fun defaultFilledVariantWinsWhenPrimaryMissingDefaultable() = runBlocking {
        val plan = ActionPlan.ToolCall("write_file", buildJsonObject { put("path", "/tmp/f") }, grant())
        val selection = ShadowPlanner().select(plan, context)
        assertTrue(selection is ShadowPlanner.Selection.Selected)
        val args = ((selection as ShadowPlanner.Selection.Selected).plan as ActionPlan.ToolCall).args
        assertEquals("overwrite", args["mode"]?.toString()?.trim('"'))
    }

    @Test
    fun allInvalidFailsLoudly() = runBlocking {
        val plan = ActionPlan.ToolCall("missing_tool", buildJsonObject {}, grant(requested = listOf("missing_tool")))
        val selection = ShadowPlanner().select(plan, context)
        assertTrue(selection is ShadowPlanner.Selection.AllInvalid)
        assertTrue((selection as ShadowPlanner.Selection.AllInvalid).diagnostics.isNotEmpty())
    }
}
