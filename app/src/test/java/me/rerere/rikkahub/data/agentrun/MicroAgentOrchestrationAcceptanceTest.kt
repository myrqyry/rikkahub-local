package me.rerere.rikkahub.data.agentrun

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.locallm.litert.ActionPlan
import me.rerere.locallm.litert.ActionPlanExecutor
import me.rerere.locallm.litert.ActionPlanResult
import me.rerere.locallm.litert.CapabilityGrant
import me.rerere.locallm.litert.LiteRtToolBridgeRegistry
import me.rerere.locallm.litert.Postcondition
import me.rerere.locallm.litert.PostconditionResult
import me.rerere.locallm.litert.PostconditionVerifier
import me.rerere.locallm.litert.ResourceBudget
import me.rerere.locallm.litert.ShadowPlanner
import me.rerere.locallm.litert.mesh.MicroAgentContext
import me.rerere.locallm.litert.mesh.MicroAgentEvent
import me.rerere.locallm.litert.mesh.MicroAgentEventMesh
import me.rerere.locallm.litert.mesh.MicroAgentEventSink
import me.rerere.locallm.litert.mesh.MicroAgentOrchestrator
import me.rerere.locallm.litert.mesh.MicroAgentTopics
import me.rerere.locallm.litert.mesh.PlanProposed
import me.rerere.locallm.litert.mesh.PlannerAgent
import me.rerere.locallm.litert.mesh.VerifierAgent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase D acceptance: the micro-agent orchestration reads status end-to-end through the real
 * deterministic stack (DefaultExecutorAgent -> ActionPlanExecutor -> ShadowPlanner ->
 * DirectToolExecutor -> tool), never bypassing a role, and the mesh correlation is preserved
 * end to end. Uses org.junit + runBlocking (no coroutines-test).
 */
class MicroAgentOrchestrationAcceptanceTest {

    private val invocations = AtomicInteger(0)
    private lateinit var readStatusTool: Tool

    private fun grantFor(tools: List<String>): CapabilityGrant =
        CapabilityGrant(requestedCapabilities = tools, grantedCapabilities = tools, rejectedCapabilities = emptyList())

    private fun readStatusPlan(): ActionPlan.ToolCall =
        ActionPlan.ToolCall(toolName = "read_status", args = buildJsonObject { }, grant = grantFor(listOf("read_status")))

    @Before
    fun setUp() {
        invocations.set(0)
        readStatusTool = Tool(
            name = "read_status",
            description = "reads status",
            parameters = { null },
            systemPrompt = { _, _ -> "" },
            needsApproval = { false },
            execute = { args ->
                invocations.incrementAndGet()
                listOf(UIMessagePart.Text("""{"status": "ok"}"""))
            },
        )
        LiteRtToolBridgeRegistry.setForRequest(listOf(readStatusTool))
    }

    @After
    fun tearDown() {
        LiteRtToolBridgeRegistry.clear()
    }

    @Test
    fun readStatusRunsThroughDeterministicStackOnce() = runBlocking {
        val scope = CoroutineScope(Job())
        val executor = ActionPlanExecutor(shadowPlanner = ShadowPlanner())

        // Direct execution through the stack: DefaultExecutorAgent would be the caller, but
        // here we prove the stack itself invokes the tool exactly once and returns Success.
        val plan = readStatusPlan()
        val result = executor.execute(plan)

        assertTrue(result is ActionPlanResult.Success)
        val output = (result as ActionPlanResult.Success).output
        assertEquals(1, output.size)
        assertTrue((output.first() as UIMessagePart.Text).text.contains("ok"))
        assertEquals(1, invocations.get())
    }

    @Test
    fun fullOrchestrationCompletesWithOneToolInvocationAndCorrelatedMesh() = runBlocking {
        val scope = CoroutineScope(Job())
        val correlationId = "corr-accept"
        val runId = "run-accept"

        // Recording sink proves the mesh carries the correlation end to end.
        val sunk = ConcurrentLinkedQueue<MicroAgentEvent>()
        val mesh = MicroAgentEventMesh(scope, sink = MicroAgentEventSink { sunk += it })

        // A shadow planner wired INSIDE ActionPlanExecutor so plan selection happens there;
        // the executor agent itself holds no tool references and just delegates.
        val actionPlanExecutor = ActionPlanExecutor(shadowPlanner = ShadowPlanner())

        val planner = PlannerAgent { _, _ ->
            PlanProposed(
                plan = readStatusPlan(),
                requestedPostconditions = listOf(Postcondition.ExecutionSucceeded),
            )
        }
        val reviewer = DefaultReviewerAgent()
        val executorAgent = DefaultExecutorAgent(actionPlanExecutor)
        val verifier = VerifierAgent { result, postconditions, _ ->
            PostconditionVerifier().verify(postconditions, emptyMap())
        }

        val orchestrator = MicroAgentOrchestrator(
            mesh = mesh,
            planner = planner,
            reviewer = reviewer,
            executor = executorAgent,
            verifier = verifier,
            idFactory = { "evt-accept-" + java.util.UUID.randomUUID().toString().take(6) },
            scope = scope,
        )
        val context = MicroAgentContext(
            runId = runId,
            correlationId = correlationId,
            conversationId = "conv-accept",
            grant = grantFor(listOf("read_status")),
            budget = ResourceBudget(),
        )

        val result = orchestrator.orchestrate("read status", context)

        // Terminal orchestration completed; postcondition (ExecutionSucceeded) passed.
        assertEquals("completed", result.status)
        assertTrue(result.postcondition is PostconditionResult.Passed)
        // Exactly one tool invocation inside the orchestration.
        assertEquals(1, invocations.get())

        // Mesh correlation: the initial request and the terminal verification event both ride
        // the SAME correlationId, and every event observed by the sink shares it.
        val planRequested = sunk.firstOrNull { it.topic == MicroAgentTopics.PLAN_REQUESTED }
        assertTrue("sink must observe PLAN_REQUESTED", planRequested != null)
        assertEquals(correlationId, planRequested?.correlationId)

        val verificationCompleted = sunk.firstOrNull { it.topic == MicroAgentTopics.VERIFICATION_COMPLETED }
        assertTrue("sink must observe VERIFICATION_COMPLETED", verificationCompleted != null)
        assertEquals(correlationId, verificationCompleted?.correlationId)

        assertTrue("every sink event must share the correlation id", sunk.all { it.correlationId == correlationId })
    }
}
