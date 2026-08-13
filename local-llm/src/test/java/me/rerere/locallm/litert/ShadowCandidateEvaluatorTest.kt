package me.rerere.locallm.litert

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowCandidateEvaluatorTest {

    private fun prop(type: String, description: String = ""): JsonObject = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    private fun tool(name: String, vararg keys: String): Tool = Tool(
        name = name,
        description = name,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    for (k in keys) put(k, prop("string"))
                },
                required = if (keys.isEmpty()) null else listOf(keys[0]),
            )
        },
        execute = { listOf(UIMessagePart.Text("ok")) },
    )

    private val catalog = mapOf(
        "write_file" to tool("write_file", "path", "text"),
        "read_file" to tool("read_file", "path"),
        "count" to tool("count", "limit"),
    )

    private val context = CompilationContext(
        toolCatalog = catalog,
        canonicalAliases = mapOf("write_f" to "write_file"),
    )

    private fun toolCall(
        toolName: String,
        args: JsonObject = buildJsonObject { },
        requested: List<String> = listOf(toolName),
        granted: List<String> = listOf(toolName),
    ): ActionPlan.ToolCall = ActionPlan.ToolCall(
        toolName = toolName,
        args = args,
        grant = CapabilityGrant(
            requestedCapabilities = requested,
            grantedCapabilities = granted,
            rejectedCapabilities = (requested - granted).toList(),
        ),
    )

    private fun candidate(id: String, plan: ActionPlan, source: ShadowCandidate.Source = ShadowCandidate.Source.PRIMARY): ShadowCandidate =
        ShadowCandidate(id = id, plan = plan, source = source)

    @Test
    fun validRanksAboveInvalidAndWins() = runBlocking {
        val valid = candidate("valid", toolCall("read_file", buildJsonObject { put("path", "a.txt") }))
        val invalid = candidate("invalid", toolCall("no_such_tool"))

        val outcome = ShadowCandidateEvaluator().evaluate(listOf(valid, invalid), context)

        assertEquals(listOf("valid", "invalid"), outcome.ranked.map { it.candidateId })
        assertEquals("valid", outcome.winner?.candidateId)
        assertEquals(1.0, outcome.ranked[0].score, 0.0)
        assertEquals(0.0, outcome.ranked[1].score, 0.0)
        assertEquals("invalid", outcome.ranked[1].compileOutcome)
    }

    @Test
    fun repairedRanksBetweenValidAndInvalid() = runBlocking {
        val valid = candidate("valid", toolCall("read_file", buildJsonObject { put("path", "a.txt") }))
        val alias = candidate("alias", toolCall("write_f", buildJsonObject { put("path", "x"); put("text", "y") }))
        val invalid = candidate("invalid", toolCall("no_such_tool"))

        val outcome = ShadowCandidateEvaluator().evaluate(listOf(alias, valid, invalid), context)

        assertEquals(listOf("valid", "alias", "invalid"), outcome.ranked.map { it.candidateId })
        assertTrue(outcome.ranked[1].score < outcome.ranked[0].score)
        assertTrue(outcome.ranked[1].score > outcome.ranked[2].score)
        assertEquals("repaired", outcome.ranked[1].compileOutcome)
        assertTrue(outcome.ranked[1].repairs.any { it.startsWith("PLAN_TOOL_001") })
    }

    @Test
    fun lossyRepairsPenalisedHarderThanLossless() = runBlocking {
        val lossless = candidate("lossless", toolCall("count", buildJsonObject { put("limit", JsonPrimitive("30")) }))
        val lossy = candidate("lossy", toolCall("read_file", buildJsonObject { put("path", "a.txt") }))

        val evaluator = ShadowCandidateEvaluator(
            compiler = object : ActionPlanCompiler {
                override suspend fun compile(plan: ActionPlan, context: CompilationContext): CompilationResult {
                    val call = plan as ActionPlan.ToolCall
                    val repair = Repair(
                        code = "PLAN_ARG_003",
                        step = "tool:${call.toolName}",
                        message = "coerced",
                        kind = Repair.Kind.COERCE_ARG,
                        lossy = call.toolName == "read_file",
                    )
                    return CompilationResult.Repaired(plan, plan, listOf(repair))
                }
            },
        )

        val outcome = evaluator.evaluate(listOf(lossless, lossy), context)

        assertEquals(listOf("lossless", "lossy"), outcome.ranked.map { it.candidateId })
        assertTrue(outcome.ranked[0].score > outcome.ranked[1].score)
    }

    @Test
    fun fullCapabilityCoverageOutranksPartial() = runBlocking {
        val full = candidate(
            "full",
            toolCall("read_file", buildJsonObject { put("path", "a.txt") }, granted = listOf("read_file")),
        )
        val partial = candidate(
            "partial",
            toolCall("read_file", buildJsonObject { put("path", "a.txt") }, granted = listOf("other_tool")),
        )

        val outcome = ShadowCandidateEvaluator().evaluate(listOf(partial, full), context)

        assertEquals(listOf("full", "partial"), outcome.ranked.map { it.candidateId })
        assertEquals(1.0, outcome.ranked[0].capabilityCoverage, 0.0)
        assertEquals(0.0, outcome.ranked[1].capabilityCoverage, 0.0)
        assertEquals(0.0, outcome.ranked[1].score, 0.0)
        assertTrue(outcome.ranked[1].reasons.any { it.startsWith("capability_coverage") })
    }

    @Test
    fun equalScoresBreakDeterministicallyOnId() = runBlocking {
        val a = candidate("b-first", toolCall("read_file", buildJsonObject { put("path", "a.txt") }))
        val b = candidate("a-first", toolCall("read_file", buildJsonObject { put("path", "a.txt") }))

        val outcome = ShadowCandidateEvaluator().evaluate(listOf(a, b), context)

        // Both compile valid + full coverage → equal scores; tie-break on id (a-first < b-first).
        assertEquals(listOf("a-first", "b-first"), outcome.ranked.map { it.candidateId })
        assertEquals("a-first", outcome.winner?.candidateId)
    }

    @Test
    fun evaluationNeverInvokesTools() = runBlocking {
        val boom = Tool(
            name = "boom",
            description = "boom",
            parameters = { null },
            execute = { throw IllegalStateException("must not be invoked") },
        )
        val boomCatalog = mapOf("boom" to boom)
        val candidate = candidate("boom", toolCall("boom"))

        val outcome = ShadowCandidateEvaluator().evaluate(listOf(candidate), CompilationContext(boomCatalog))

        assertEquals("valid", outcome.ranked[0].compileOutcome)
        assertEquals(1.0, outcome.ranked[0].score, 0.0)
        assertEquals("boom", outcome.winner?.candidateId)
    }

    @Test
    fun emptyCandidatesYieldNoWinner() = runBlocking {
        val outcome = ShadowCandidateEvaluator().evaluate(emptyList(), context)
        assertTrue(outcome.ranked.isEmpty())
        assertNull(outcome.winner)
    }
}
