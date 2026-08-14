package me.rerere.rikkahub.data.agentrun

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.locallm.litert.ActionPlan
import me.rerere.locallm.litert.ActionPlanResult
import me.rerere.locallm.litert.CapabilityGrant
import me.rerere.locallm.litert.CapabilityScopes
import me.rerere.locallm.litert.PostconditionResult
import me.rerere.locallm.litert.zero.ProcedureCache
import me.rerere.locallm.litert.zero.ProcedureReviewState
import me.rerere.locallm.litert.zero.ProcedureMiner
import me.rerere.locallm.litert.zero.StepOutputRef
import me.rerere.locallm.litert.zero.ToolExecution
import me.rerere.locallm.litert.zero.ZeroProcedure
import me.rerere.locallm.litert.zero.ZeroProcedureEngine
import me.rerere.locallm.litert.zero.ZeroProcedureReceipt
import me.rerere.locallm.litert.zero.ZeroProcedureReceiptSink
import me.rerere.locallm.litert.zero.ZeroStep
import me.rerere.locallm.litert.outputEqualsText
import me.rerere.rikkahub.data.ai.revision.AsyncAttachmentGate
import me.rerere.rikkahub.data.ai.revision.ConversationRevisionGuard
import me.rerere.rikkahub.data.ai.revision.ConversationRevisionSource
import me.rerere.rikkahub.data.ai.revision.ConversationSnapshot
import me.rerere.rikkahub.data.ai.revision.RevisionCheckResult
import me.rerere.rikkahub.data.ai.revision.RevisionCommitPolicy
import me.rerere.rikkahub.data.db.entity.ZeroProcedureDao
import me.rerere.rikkahub.data.db.entity.ZeroProcedureEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BcBridgeIntegrationTest {

    private val testJson = Json { ignoreUnknownKeys = true }

    private fun tool(name: String, out: String): Tool = Tool(
        name = name,
        description = name,
        parameters = { null },
        systemPrompt = { _, _ -> "" },
        needsApproval = { false },
        execute = { listOf(UIMessagePart.Text(out)) },
    )

    private val catalog = mapOf(
        "echo" to tool("echo", "echoed"),
        "fetch" to tool("fetch", """{"status": "ok", "data": 42}"""),
    )

    private class FakeZeroProcedureDao : ZeroProcedureDao {
        val rows = linkedMapOf<String, ZeroProcedureEntity>()
        private val flow = MutableStateFlow<List<ZeroProcedureEntity>>(emptyList())
        private fun publish() { flow.value = rows.values.toList() }
        override fun observeAll(): Flow<List<ZeroProcedureEntity>> = flow
        override suspend fun listAll(): List<ZeroProcedureEntity> = rows.values.toList()
        override suspend fun getById(id: String): ZeroProcedureEntity? = rows[id]
        override fun observeById(id: String): Flow<ZeroProcedureEntity?> = flow.map { list -> list.firstOrNull { it.id == id } }
        override suspend fun listBySource(source: String): List<ZeroProcedureEntity> = rows.values.filter { it.source == source }
        override suspend fun upsert(entity: ZeroProcedureEntity) { rows[entity.id] = entity; publish() }
        override suspend fun update(entity: ZeroProcedureEntity) { rows[entity.id] = entity; publish() }
        override suspend fun deleteById(id: String): Int { val removed = rows.remove(id) != null; if (removed) publish(); return if (removed) 1 else 0 }
    }

    @Test
    fun endToEnd_compileSelectAuthorizeExecuteVerifyMineRejectStale() = runBlocking {
        val dao = FakeZeroProcedureDao()
        val repository = ZeroProcedureRepository(dao, testJson)

        // 1. A USER procedure with a postcondition.
        val procedure = ZeroProcedure(
            id = "user-fetch-sum",
            description = "fetch and check",
            steps = listOf(
                ZeroStep(stepId = "fetch", tool = "fetch", args = buildJsonObject { put("url", "https://example.test") }),
            ),
            failFast = true,
            postconditions = listOf(
                outputEqualsText(stepId = "fetch", path = listOf("status"), expected = "ok"),
            ),
        )
        repository.put(procedure)

        // 2. Capability grant authorizes the fetch tool.
        val grant = CapabilityGrant(
            requestedCapabilities = listOf("fetch"),
            grantedCapabilities = listOf("fetch"),
            rejectedCapabilities = emptyList(),
            scopes = CapabilityScopes(),
        )
        val plan = ActionPlan.ProcedureCall(procedureId = "user-fetch-sum", inputs = buildJsonObject {}, grant = grant)

        // 3. Execute with per-step receipts + postcondition verdict captured by the sink.
        var recorded: ZeroProcedureReceipt? = null
        var verdict: PostconditionResult? = null
        val sink = ZeroProcedureReceiptSink { receipt, postcondition ->
            recorded = receipt
            verdict = postcondition
        }
        val adapter = ZeroProcedureExecutorAdapter(repository = repository, engine = ZeroProcedureEngine(), toolCatalog = catalog, receiptSink = sink)

        val result = adapter.execute(plan)
        assertTrue(result is ActionPlanResult.Success)
        val receipt = recorded!!
        assertEquals("user-fetch-sum", receipt.procedureId)
        assertTrue(receipt.steps.any { it.toolName == "fetch" })
        assertEquals(me.rerere.locallm.litert.zero.StepStatus.SUCCEEDED, receipt.steps.single().status)
        assertEquals(PostconditionResult.Passed, verdict)

        // 4. Postcondition failure is distinct from execution success (guarded below by a bad expectation path).
        val failing = ZeroProcedure(
            id = "user-wrong", description = "wrong", steps = listOf(ZeroStep("fetch", "fetch", buildJsonObject {})),
            postconditions = listOf(outputEqualsText("fetch", listOf("status"), expected = "nope")),
        )
        repository.put(failing)
        val bad = adapter.execute(ActionPlan.ProcedureCall("user-wrong", buildJsonObject {}, grant))
        assertTrue(bad is ActionPlanResult.Failed && bad.errorMessage.contains("postcondition_failed"))

        // 5. Mine persisted history: repeated successful tool events produce a MINED CANDIDATE.
        val persistedHistory = listOf(
            ToolExecution(toolName = "fetch", args = buildJsonObject {}, atMs = 1L),
            ToolExecution(toolName = "fetch", args = buildJsonObject {}, atMs = 2L),
            ToolExecution(toolName = "echo", args = buildJsonObject {}, atMs = 3L),
        )
        val feed = ProcedureMiningFeed(
            repository = repository,
            miner = ProcedureMiner(minSteps = 1, minSupport = 2, maxSteps = 10),
            executionsBeforeMine = 100,
            persistedHistory = { persistedHistory },
        )
        feed.minePersisted()
        val mined = repository.listByReviewState(ProcedureReviewState.CANDIDATE)
        assertTrue(mined.any { it.id.startsWith("mined_") && it.steps.first().tool == "fetch" })
        val minedRow = dao.rows.values.first { it.source == "MINED" }
        assertTrue(!minedRow.enabled)
        assertEquals(ProcedureReviewState.CANDIDATE, repository.reviewStateOf(minedRow.id))

        // 6. A stale conversation revision rejects async attachment.
        val current = ConversationSnapshot("c1", "b1", 9L)
        val gate = AsyncAttachmentGate(
            ConversationRevisionGuard(source = object : ConversationRevisionSource {
                override suspend fun currentState(conversationId: String) = current
            }),
        )
        val decision = gate.decide(capturedAtStart = ConversationSnapshot("c1", "b1", 10L), policy = RevisionCommitPolicy.REQUIRE_EXACT_MATCH)
        val reject = decision as AsyncAttachmentGate.Decision.Reject
        assertTrue(reject.reason.contains("regressed"))
    }
}
