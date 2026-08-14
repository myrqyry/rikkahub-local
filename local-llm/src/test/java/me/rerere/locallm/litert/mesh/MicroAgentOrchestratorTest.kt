package me.rerere.locallm.litert.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.locallm.litert.ActionPlan
import me.rerere.locallm.litert.ActionPlanResult
import me.rerere.locallm.litert.CapabilityGrant
import me.rerere.locallm.litert.Postcondition
import me.rerere.locallm.litert.PostconditionResult
import me.rerere.locallm.litert.ResourceBudget
import me.rerere.locallm.litert.ShadowPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class MicroAgentOrchestratorTest {

    private var idSeq = 0L
    private fun nextId() = "o-id-${idSeq++}"

    private fun grantFor(tools: List<String>): CapabilityGrant =
        CapabilityGrant(requestedCapabilities = tools, grantedCapabilities = tools, rejectedCapabilities = emptyList())

    private fun toolCall(name: String): ActionPlan.ToolCall =
        ActionPlan.ToolCall(toolName = name, args = buildJsonObject { }, grant = grantFor(listOf(name)))

    @Test
    fun endToEndLifecycleDrivesRolesThroughMesh() = runBlocking {
        val scope = CoroutineScope(Job())
        var correlation = ""
        val mesh = MicroAgentEventMesh(scope)
        var eventIdSeq = 0L
        val plan = toolCall("read_status")

        val planned = AtomicInteger(0)
        val reviewed = AtomicInteger(0)
        val executed = AtomicInteger(0)
        val verified = AtomicInteger(0)

        val planner = PlannerAgent { request, context ->
            assertEquals("read status", request)
            planned.incrementAndGet()
            correlation = context.correlationId
            PlanProposed(plan = plan, requestedPostconditions = listOf(Postcondition.ExecutionSucceeded))
        }
        val reviewer = ReviewerAgent { _, _, _ ->
            reviewed.incrementAndGet()
            ReviewDecision.Accepted(plan = plan, postconditions = listOf(Postcondition.ExecutionSucceeded))
        }
        val executor = ExecutorAgent { _, _, _, _ ->
            executed.incrementAndGet()
            ExecutionResult(result = ActionPlanResult.Success(emptyList()), receiptRef = "receipt-1")
        }
        val verifier = VerifierAgent { _, _, _ ->
            verified.incrementAndGet()
            PostconditionResult.Passed
        }

        val orchestrator = MicroAgentOrchestrator(
            mesh = mesh,
            planner = planner,
            reviewer = reviewer,
            executor = executor,
            verifier = verifier,
            idFactory = { "evt-${eventIdSeq++}" },
            scope = scope,
        )
        val context = MicroAgentContext(
            runId = "run-1",
            correlationId = correlation,
            conversationId = "conv-1",
            grant = grantFor(listOf("read_status")),
            budget = ResourceBudget(),
        )

        val result = orchestrator.orchestrate("read status", context)

        assertEquals("completed", result.status)
        assertEquals(1, planned.get())
        assertEquals(1, reviewed.get())
        assertEquals(1, executed.get())
        assertEquals(1, verified.get())
        assertNotNull(result.postcondition)
        assertTrue(result.postcondition is PostconditionResult.Passed)
    }

    @Test
    fun reviewerRejectionAbortsWithoutExecution() = runBlocking {
        val scope = CoroutineScope(Job())
        var eventIdSeq = 0L
        val mesh = MicroAgentEventMesh(scope)
        val plan = toolCall("read_status")

        val executed = AtomicInteger(0)
        val planner = PlannerAgent { _, _ -> PlanProposed(plan, emptyList()) }
        val reviewer = ReviewerAgent { _, _, _ -> ReviewDecision.Rejected(code = "PLAN_RISK", reason = "unknown tool") }
        val executor = ExecutorAgent { _, _, _, _ -> executed.incrementAndGet(); ExecutionResult(ActionPlanResult.Failed("nope")) }
        val verifier = VerifierAgent { _, _, _ -> PostconditionResult.Passed }

        val orchestrator = MicroAgentOrchestrator(mesh, planner, reviewer, executor, verifier, { "evt-${eventIdSeq++}" }, { "none" }, scope)
        val context = MicroAgentContext("run-2", "corr-2", null, grantFor(emptyList()), ResourceBudget())

        val result = orchestrator.orchestrate("read status", context)

        assertEquals("failed", result.status)
        assertTrue(result.summary.contains("reviewer rejected"))
        assertEquals(0, executed.get())
    }

    @Test
    fun postconditionFailureFlagsOrchestrationFailed() = runBlocking {
        val scope = CoroutineScope(Job())
        var eventIdSeq = 0L
        val mesh = MicroAgentEventMesh(scope)
        val plan = toolCall("read_status")

        val planner = PlannerAgent { _, _ -> PlanProposed(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val reviewer = ReviewerAgent { _, _, _ -> ReviewDecision.Accepted(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val executor = ExecutorAgent { _, _, _, _ -> ExecutionResult(ActionPlanResult.Success(emptyList())) }
        val verifier = VerifierAgent { _, _, _ -> PostconditionResult.Failed("output missing", "no output") }

        val orchestrator = MicroAgentOrchestrator(mesh, planner, reviewer, executor, verifier, { "evt-${eventIdSeq++}" }, { "none" }, scope)
        val context = MicroAgentContext("run-3", "corr-3", null, grantFor(listOf("read_status")), ResourceBudget())

        val result = orchestrator.orchestrate("read status", context)

        assertEquals("failed", result.status)
        assertTrue(result.postcondition is PostconditionResult.Failed)
    }

    @Test
    fun orchestratorIsNotARoleBypass() = runBlocking {
        val scope = CoroutineScope(Job())
        var eventIdSeq = 0L
        val mesh = MicroAgentEventMesh(scope)
        val plan = toolCall("read_status")

        // shadowPlanner passed to executor must be the one the orchestrator provided (null here = not wired)
        val executor = ExecutorAgent { _, _, _, shadowPlanner ->
            if (shadowPlanner != null) throw AssertionError("executor must not receive a shadow planner to bypass")
            ExecutionResult(ActionPlanResult.Success(emptyList()))
        }

        val orchestrator = MicroAgentOrchestrator(
            mesh, { _, _ -> PlanProposed(plan, emptyList()) },
            { _, _, _ -> ReviewDecision.Accepted(plan, emptyList()) },
            executor,
            { _, _, _ -> PostconditionResult.Passed },
            { "evt-${eventIdSeq++}" },
            scope = scope,
        )
        val context = MicroAgentContext("run-4", "corr-4", null, grantFor(listOf("read_status")), ResourceBudget())

        val result = orchestrator.orchestrate("read status", context)
        assertEquals("completed", result.status)
    }

    @Test
    fun allShadowCandidatesInvalidAbortsExecution() = runBlocking {
        val scope = CoroutineScope(Job())
        var eventIdSeq = 0L
        val mesh = MicroAgentEventMesh(scope)
        val plan = toolCall("read_status")

        // ShadowPlanner runs inside ActionPlanExecutor; when every candidate is invalid the
        // stack fails loudly and never invokes a tool. Simulate that here by having the
        // executor adapter return a compile-invalid Failure (rather than executing anything);
        // the verifier must not treat it as success and the orchestration must fail visibly.
        val executed = AtomicInteger(0)
        val planner = PlannerAgent { _, _ -> PlanProposed(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val reviewer = ReviewerAgent { _, _, _ -> ReviewDecision.Accepted(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val executor = ExecutorAgent { _, _, _, _ ->
            executed.incrementAndGet()
            ExecutionResult(ActionPlanResult.Failed("plan_compile_invalid"))
        }
        val verifier = VerifierAgent { result, _, _ ->
            if (result is ActionPlanResult.Success) PostconditionResult.Passed
            else PostconditionResult.Failed("execution failed", (result as? ActionPlanResult.Failed)?.errorMessage ?: "failed")
        }

        val orchestrator = MicroAgentOrchestrator(mesh, planner, reviewer, executor, verifier, { "evt-${eventIdSeq++}" }, { "none" }, scope)
        val context = MicroAgentContext("run-6", "corr-6", null, grantFor(listOf("read_status")), ResourceBudget())

        val result = orchestrator.orchestrate("read status", context)

        assertEquals("failed", result.status)
        assertEquals(1, executed.get())
        assertTrue(result.postcondition is PostconditionResult.Failed)
    }

    @Test
    fun executorThrowsIsolatedVerifierNotCalled() = runBlocking {
        val scope = CoroutineScope(Job())
        var eventIdSeq = 0L
        val mesh = MicroAgentEventMesh(scope)
        val plan = toolCall("read_status")
        val verified = AtomicInteger(0)

        val planner = PlannerAgent { _, _ -> PlanProposed(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val reviewer = ReviewerAgent { _, _, _ -> ReviewDecision.Accepted(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val executor = ExecutorAgent { _, _, _, _ -> throw RuntimeException("boom") }
        val verifier = VerifierAgent { _, _, _ -> verified.incrementAndGet(); PostconditionResult.Passed }

        val orchestrator = MicroAgentOrchestrator(mesh, planner, reviewer, executor, verifier, { "evt-${eventIdSeq++}" }, { "none" }, scope)
        val context = MicroAgentContext("run-7", "corr-7", null, grantFor(listOf("read_status")), ResourceBudget())

        // The executor subscriber's throw is isolated by the mesh worker: EXECUTION_COMPLETED
        // never fires, so the terminal event never arrives and orchestrate() would block
        // forever. Assert it times out (execution isolated, verifier never reached).
        val outcome = runCatching { withTimeout(500) { orchestrator.orchestrate("read status", context) } }
        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull() is TimeoutCancellationException)
        assertEquals(0, verified.get())

        // The mesh stays alive: a later event is still delivered to a new subscriber.
        val later = ConcurrentLinkedQueue<String>()
        mesh.subscribe("probe", "beta") { later += it.sourceAgentId }
        mesh.publish(
            MicroAgentEvent(
                sourceAgentId = "src", topic = "beta", eventId = "probe-1",
                correlationId = "corr-probe",
            ),
        )
        val deadline = System.currentTimeMillis() + 2000
        while (later.isEmpty() && System.currentTimeMillis() < deadline) delay(5)
        assertEquals(listOf("src"), later.toList())
    }

    @Test
    fun duplicateExecutionRequestEffectRunsOnce() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val executions = AtomicInteger(0)
        mesh.subscribe("executor", MicroAgentTopics.EXECUTION_REQUESTED) { executions.incrementAndGet() }
        val plan = toolCall("read_status")
        val dedupeKey = "effect-write"
        val first = MicroAgentEvent(
            sourceAgentId = "orch",
            topic = MicroAgentTopics.EXECUTION_REQUESTED,
            eventId = "er-1",
            correlationId = "corr-x",
            payload = buildJsonObject {
                put("message", JsonPrimitive(MicroAgentMessageCodec.encode(ExecutionRequested(plan, emptyList()))))
            },
            dedupeKey = dedupeKey,
        )
        val second = first.copy(eventId = "er-2", correlationId = "corr-y")

        assertTrue(mesh.publish(first) is MeshPublishResult.Delivered)
        assertTrue(mesh.publish(second) is MeshPublishResult.Duplicate)

        val deadline = System.currentTimeMillis() + 2000
        while (executions.get() < 1 && System.currentTimeMillis() < deadline) delay(5)
        assertEquals(1, executions.get())
    }

    @Test
    fun cancelBeforeExecutionZeroEffects() = runBlocking {
        val scope = CoroutineScope(Job())
        val mesh = MicroAgentEventMesh(scope)
        val executions = AtomicInteger(0)
        mesh.subscribe("executor", MicroAgentTopics.EXECUTION_REQUESTED) { executions.incrementAndGet() }
        val plan = toolCall("read_status")

        mesh.cancel("corr-cancel")
        val result = mesh.publish(
            MicroAgentEvent(
                sourceAgentId = "orch",
                topic = MicroAgentTopics.EXECUTION_REQUESTED,
                eventId = "er-cancel",
                correlationId = "corr-cancel",
                payload = buildJsonObject {
                    put("message", JsonPrimitive(MicroAgentMessageCodec.encode(ExecutionRequested(plan, emptyList()))))
                },
            ),
        )

        val delivery = (result as MeshPublishResult.Delivered).delivery
        assertEquals(emptyList<String>(), delivery.deliveredTo)
        delay(50)
        assertEquals(0, executions.get())
    }

    @Test
    fun successfulRunPublishesExactlyOneTerminalCompletedEvent() = runBlocking {
        val scope = CoroutineScope(Job())
        var eventIdSeq = 0L
        val mesh = MicroAgentEventMesh(scope)
        val plan = toolCall("read_status")

        // record the terminal orchestration topic(s) observed for the correlation
        val terminalTopics = ConcurrentLinkedQueue<String>()
        mesh.subscribe("probe", "orchestration.completed", "orchestration.failed", "orchestration.cancelled") { terminalTopics += it.topic }

        val planner = PlannerAgent { _, _ -> PlanProposed(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val reviewer = ReviewerAgent { _, _, _ -> ReviewDecision.Accepted(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val executor = ExecutorAgent { _, _, _, _ -> ExecutionResult(ActionPlanResult.Success(emptyList())) }
        val verifier = VerifierAgent { _, _, _ -> PostconditionResult.Passed }
        val orchestrator = MicroAgentOrchestrator(mesh, planner, reviewer, executor, verifier, { "evt-${eventIdSeq++}" }, { "none" }, scope)
        val context = MicroAgentContext("run-10", "corr-10", null, grantFor(listOf("read_status")), ResourceBudget())

        val result = orchestrator.orchestrate("read status", context)
        assertEquals("completed", result.status)

        val deadline = System.currentTimeMillis() + 2000
        while (terminalTopics.isEmpty() && System.currentTimeMillis() < deadline) delay(5)
        assertEquals(listOf("orchestration.completed"), terminalTopics.toList())
    }

    @Test
    fun failedPostconditionPublishesOrchestrationFailedNotCompleted() = runBlocking {
        val scope = CoroutineScope(Job())
        var eventIdSeq = 0L
        val mesh = MicroAgentEventMesh(scope)
        val plan = toolCall("read_status")

        val terminalTopics = ConcurrentLinkedQueue<String>()
        mesh.subscribe("probe", "orchestration.completed", "orchestration.failed", "orchestration.cancelled") { terminalTopics += it.topic }

        val planner = PlannerAgent { _, _ -> PlanProposed(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val reviewer = ReviewerAgent { _, _, _ -> ReviewDecision.Accepted(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val executor = ExecutorAgent { _, _, _, _ -> ExecutionResult(ActionPlanResult.Success(emptyList())) }
        val verifier = VerifierAgent { _, _, _ -> PostconditionResult.Failed("output missing", "no output") }
        val orchestrator = MicroAgentOrchestrator(mesh, planner, reviewer, executor, verifier, { "evt-${eventIdSeq++}" }, { "none" }, scope)
        val context = MicroAgentContext("run-11", "corr-11", null, grantFor(listOf("read_status")), ResourceBudget())

        val result = orchestrator.orchestrate("read status", context)
        assertEquals("failed", result.status)

        val deadline = System.currentTimeMillis() + 2000
        while (terminalTopics.isEmpty() && System.currentTimeMillis() < deadline) delay(5)
        assertEquals(listOf("orchestration.failed"), terminalTopics.toList())
    }

    @Test
    fun cancellationDuringExecutionPublishesOrchestrationCancelledAndLateVerificationCannotReplaceTerminal() = runBlocking {
        val scope = CoroutineScope(Job())
        var eventIdSeq = 0L
        // Cancelled correlations suppress delivery to mesh subscribers (D7); the terminal
        // event is still recorded via the sink. Observe it there.
        val sunkTopics = ConcurrentLinkedQueue<String>()
        val mesh = MicroAgentEventMesh(scope, sink = MicroAgentEventSink { sunkTopics += it.topic })
        val plan = toolCall("read_status")

        val executed = AtomicInteger(0)
        val executor = ExecutorAgent { _, _, _, _ ->
            executed.incrementAndGet()
            ExecutionResult(ActionPlanResult.Success(emptyList()))
        }
        val verifier = VerifierAgent { _, _, _ -> PostconditionResult.Passed }
        val planner = PlannerAgent { _, _ -> PlanProposed(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val reviewer = ReviewerAgent { _, _, _ -> ReviewDecision.Accepted(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val orchestrator = MicroAgentOrchestrator(mesh, planner, reviewer, executor, verifier, { "evt-${eventIdSeq++}" }, { "none" }, scope)
        val context = MicroAgentContext("run-12", "corr-12", null, grantFor(listOf("read_status")), ResourceBudget())

        mesh.cancel("corr-12")
        val result = orchestrator.orchestrate("read status", context)
        assertEquals("cancelled", result.status)

        val deadline = System.currentTimeMillis() + 2000
        while (sunkTopics.isEmpty() && System.currentTimeMillis() < deadline) delay(5)
        assertEquals(0, executed.get())
        assertTrue(sunkTopics.contains("orchestration.cancelled"))
        // Late events cannot replace the terminal state: a second cancel-driven terminal
        // event is suppressed (at-most-one terminal per correlation).
        mesh.cancel("corr-12")
        delay(100)
        assertEquals(1, sunkTopics.count { it == "orchestration.cancelled" })
    }

    @Test
    fun staleRevisionAttachmentIsBackgroundedNotOrchestrationFailure() = runBlocking {
        val scope = CoroutineScope(Job())
        var eventIdSeq = 0L
        val mesh = MicroAgentEventMesh(scope)
        val plan = toolCall("read_status")

        // Work succeeded, verification passed, but the conversation moved on: attachment is
        // backgrounded_stale_revision — this must NOT be classified as orchestration failure.
        val terminalTopics = ConcurrentLinkedQueue<String>()
        mesh.subscribe("probe", "orchestration.completed", "orchestration.failed", "orchestration.cancelled") { terminalTopics += it.topic }

        val planner = PlannerAgent { _, _ -> PlanProposed(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val reviewer = ReviewerAgent { _, _, _ -> ReviewDecision.Accepted(plan, listOf(Postcondition.ExecutionSucceeded)) }
        val executor = ExecutorAgent { _, _, _, _ -> ExecutionResult(ActionPlanResult.Success(emptyList())) }
        val verifier = VerifierAgent { _, _, _ -> PostconditionResult.Passed }
        val orchestrator = MicroAgentOrchestrator(
            mesh, planner, reviewer, executor, verifier,
            idFactory = { "evt-${eventIdSeq++}" },
            attachmentResolver = { "backgrounded_stale_revision" },
            scope = scope,
        )
        val context = MicroAgentContext("run-13", "corr-13", "conv-1", grantFor(listOf("read_status")), ResourceBudget())

        val result = orchestrator.orchestrate("read status", context)
        // Work itself succeeded — NOT a failure despite stale attachment.
        assertEquals("completed", result.status)

        val deadline = System.currentTimeMillis() + 2000
        while (terminalTopics.isEmpty() && System.currentTimeMillis() < deadline) delay(5)
        assertEquals(listOf("orchestration.completed"), terminalTopics.toList())
    }
}
