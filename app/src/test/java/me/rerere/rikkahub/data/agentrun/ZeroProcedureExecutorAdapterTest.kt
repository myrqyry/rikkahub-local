package me.rerere.rikkahub.data.agentrun

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.locallm.litert.ActionPlan
import me.rerere.locallm.litert.ActionPlanResult
import me.rerere.locallm.litert.CapabilityGrant
import me.rerere.locallm.litert.CapabilityScopes
import me.rerere.locallm.litert.zero.ZeroProcedure
import me.rerere.locallm.litert.zero.ZeroProcedureEngine
import me.rerere.locallm.litert.zero.ZeroStep
import me.rerere.rikkahub.data.db.entity.ZeroProcedureDao
import me.rerere.rikkahub.data.db.entity.ZeroProcedureEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

private fun echoTool(): Tool = Tool(
    name = "echo",
    description = "echoes input",
    parameters = { null },
    systemPrompt = { _, _ -> "" },
    needsApproval = { false },
    execute = { args -> listOf(UIMessagePart.Text(args.toString())) },
)

private fun grantFor(tools: List<String>): CapabilityGrant = CapabilityGrant(
    requestedCapabilities = tools,
    grantedCapabilities = tools,
    rejectedCapabilities = emptyList(),
    scopes = CapabilityScopes(),
)

private fun call(id: String, grant: CapabilityGrant) = ActionPlan.ProcedureCall(
    procedureId = id,
    inputs = buildJsonObject { },
    grant = grant,
)

class ZeroProcedureExecutorAdapterTest {

    private fun dao(): ZeroProcedureDao {
        val rows = linkedMapOf<String, ZeroProcedureEntity>()
        return object : ZeroProcedureDao {
            override fun observeAll(): Flow<List<ZeroProcedureEntity>> = MutableStateFlow(rows.values.toList())
            override suspend fun listAll(): List<ZeroProcedureEntity> = rows.values.toList()
            override suspend fun getById(id: String): ZeroProcedureEntity? = rows[id]
            override fun observeById(id: String): Flow<ZeroProcedureEntity?> = MutableStateFlow(rows[id])
            override suspend fun listBySource(source: String): List<ZeroProcedureEntity> = rows.values.filter { it.source == source }
            override suspend fun upsert(e: ZeroProcedureEntity) { rows[e.id] = e }
            override suspend fun update(e: ZeroProcedureEntity) { rows[e.id] = e }
            override suspend fun deleteById(id: String): Int = if (rows.remove(id) != null) 1 else 0
        }
    }

    private fun repository(seed: ZeroProcedure? = null): ZeroProcedureRepository {
        val d = dao()
        val repo = ZeroProcedureRepository(d)
        if (seed != null) runBlocking { repo.put(seed) }
        return repo
    }

    private fun adapter(repo: ZeroProcedureRepository, tools: Map<String, Tool> = mapOf("echo" to echoTool())) =
        ZeroProcedureExecutorAdapter(repo, ZeroProcedureEngine(), tools)

    private fun proc(id: String, vararg tools: String) = ZeroProcedure(
        id = id,
        description = "proc-$id",
        steps = tools.mapIndexed { i, t -> ZeroStep(stepId = "s$i", tool = t, args = buildJsonObject { }) },
    )

    @Test
    fun unknownProcedureReturnsNotFound() = runBlocking {
        val result = adapter(repository()).execute(call("missing", grantFor(listOf("echo"))))
        assertTrue(result is ActionPlanResult.Failed)
        assertTrue((result as ActionPlanResult.Failed).errorMessage.contains("procedure_not_found"))
    }

    @Test
    fun compileInvalidReturnsFailure() = runBlocking {
        val repo = repository(proc("p1", "no_such_tool"))
        val result = adapter(repo).execute(call("p1", grantFor(listOf("no_such_tool"))))
        assertTrue(result is ActionPlanResult.Failed)
        assertTrue((result as ActionPlanResult.Failed).errorMessage.contains("procedure_compile_invalid"))
    }

    @Test
    fun capabilityRejectedWhenStepToolNotGranted() = runBlocking {
        val repo = repository(proc("p2", "echo"))
        // grant allows nothing
        val result = adapter(repo).execute(call("p2", grantFor(emptyList())))
        assertTrue(result is ActionPlanResult.CapabilityRejected)
        assertEquals("echo", (result as ActionPlanResult.CapabilityRejected).capability)
    }

    @Test
    fun disabledProcedureReturnsNotFound() = runBlocking {
        val repo = repository(proc("p3", "echo"))
        repo.setEnabled("p3", false)
        val result = adapter(repo).execute(call("p3", grantFor(listOf("echo"))))
        assertTrue(result is ActionPlanResult.Failed)
        assertTrue((result as ActionPlanResult.Failed).errorMessage.contains("procedure_not_found"))
    }

    @Test
    fun successfulRunReturnsTextSummary() = runBlocking {
        val repo = repository(proc("p4", "echo"))
        val result = adapter(repo).execute(call("p4", grantFor(listOf("echo"))))
        assertTrue(result is ActionPlanResult.Success)
        val parts = (result as ActionPlanResult.Success).output
        assertEquals(1, parts.size)
        assertTrue((parts[0] as UIMessagePart.Text).text.contains("proc-p4"))
    }
}
