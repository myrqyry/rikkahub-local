package me.rerere.locallm.litert.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.rerere.locallm.litert.Postcondition
import me.rerere.locallm.litert.PostconditionResult

/**
 * D5: lifecycle owner for a single multi-agent orchestration.
 *
 * Creates the correlation id, run id, cancellation boundary, subscribes the four
 * role adapters to the mesh, publishes the initial request, and awaits the terminal
 * orchestration event. The agents genuinely react through the mesh — this is not a
 * decorative `run()` wrapper.
 *
 * This class owns lifecycle only. Execution authority stays in the existing
 * deterministic stack (ShadowPlanner -> compiler -> broker -> ActionPlanExecutor).
 */
/**
 * D11: terminal-semantic invariant.
 *
 * `VERIFICATION_COMPLETED` is the Verifier finishing its job, NOT the end of the
 * correlated operation. The whole orchestration reaches a terminal state only via
 * a single `ORCHESTRATION_COMPLETED` / `ORCHESTRATION_FAILED` / `ORCHESTRATION_CANCELLED`
 * event, published after final attachment resolution (D10).
 *
 * Invariant: **every accepted correlation has at most one terminal orchestration
 * event.** `PLAN_*`, `EXECUTION_*`, `VERIFICATION_*` are never terminal, and a late
 * event can never replace an already-published terminal state.
 */
class MicroAgentOrchestrator(
    private val mesh: MicroAgentEventMesh,
    private val planner: PlannerAgent,
    private val reviewer: ReviewerAgent,
    private val executor: ExecutorAgent,
    private val verifier: VerifierAgent,
    private val idFactory: () -> String = { "id-" + java.util.UUID.randomUUID().toString().take(8) },
    private val attachmentResolver: (conversationId: String?) -> String = { "none" },
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private val completion = Channel<OrchestrationResult>(1)
    private val pendingPostconditions = HashMap<String, List<Postcondition>>()
    private val terminalPublished = HashSet<String>()

    /**
     * Runs one orchestration for [request] under [context].
     *
     * @return the terminal result; the final action plan result and postcondition
     *         verdict are carried on [OrchestrationResult] so the caller can decide
     *         conversation attachment via AsyncAttachmentGate.
     */
    suspend fun orchestrate(request: String, context: MicroAgentContext): OrchestrationResult {
        val correlationId = context.correlationId
        mesh.subscribe("planner", MicroAgentTopics.PLAN_REQUESTED) { e ->
            if (mesh.isCancelled(e.correlationId)) return@subscribe publishTerminal(correlationId, e, "cancelled", "orchestration cancelled", null, null)
            val msg = MicroAgentMessageCodec.decode(e.payloadString()) as PlanRequested
            val proposal = planner.propose(msg.userRequest, context)
            mesh.publish(newEvent(correlationId, e, "planner", MicroAgentTopics.PLAN_PROPOSED, MicroAgentMessageCodec.encode(proposal), e.hopCount + 1))
        }
        mesh.subscribe("reviewer", MicroAgentTopics.PLAN_PROPOSED) { e ->
            val msg = MicroAgentMessageCodec.decode(e.payloadString()) as PlanProposed
            val decision = reviewer.review(msg.plan, msg.requestedPostconditions, context)
            mesh.publish(newEvent(correlationId, e, "reviewer", MicroAgentTopics.PLAN_REVIEWED, MicroAgentMessageCodec.encode(decision), e.hopCount + 1))
        }
        mesh.subscribe("orch-reviewed", MicroAgentTopics.PLAN_REVIEWED) { e ->
            if (mesh.isCancelled(e.correlationId)) return@subscribe publishTerminal(correlationId, e, "cancelled", "orchestration cancelled", null, null)
            when (val decision = MicroAgentMessageCodec.decode(e.payloadString()) as ReviewDecision) {
                is ReviewDecision.Accepted -> {
                    pendingPostconditions[e.correlationId] = decision.postconditions
                    mesh.publish(newEvent(correlationId, e, "orchestrator", MicroAgentTopics.EXECUTION_REQUESTED, MicroAgentMessageCodec.encode(ExecutionRequested(decision.plan, decision.postconditions)), e.hopCount + 1))
                }
                is ReviewDecision.Rejected ->
                    publishTerminal(correlationId, e, "rejected", "reviewer rejected: ${decision.reason}", null, null)
            }
        }
        mesh.subscribe("executor", MicroAgentTopics.EXECUTION_REQUESTED) { e ->
            if (mesh.isCancelled(e.correlationId)) return@subscribe publishTerminal(correlationId, e, "cancelled", "orchestration cancelled", null, null)
            val msg = MicroAgentMessageCodec.decode(e.payloadString()) as ExecutionRequested
            val outcome = executor.execute(msg.plan, msg.postconditions, context, shadowPlanner = null)
            mesh.publish(newEvent(correlationId, e, "executor", MicroAgentTopics.EXECUTION_COMPLETED, MicroAgentMessageCodec.encode(outcome), e.hopCount + 1))
        }
        mesh.subscribe("orch-executed", MicroAgentTopics.EXECUTION_COMPLETED) { e ->
            if (mesh.isCancelled(e.correlationId)) return@subscribe publishTerminal(correlationId, e, "cancelled", "orchestration cancelled", null, null)
            val outcome = MicroAgentMessageCodec.decode(e.payloadString()) as ExecutionResult
            val postconditions = pendingPostconditions[e.correlationId] ?: emptyList()
            mesh.publish(newEvent(correlationId, e, "orchestrator", MicroAgentTopics.VERIFICATION_REQUESTED, MicroAgentMessageCodec.encode(VerificationRequested(outcome.result, postconditions)), e.hopCount + 1))
        }
        mesh.subscribe("verifier", MicroAgentTopics.VERIFICATION_REQUESTED) { e ->
            if (mesh.isCancelled(e.correlationId)) return@subscribe publishTerminal(correlationId, e, "cancelled", "orchestration cancelled", null, null)
            val msg = MicroAgentMessageCodec.decode(e.payloadString()) as VerificationRequested
            val verdict = verifier.verify(msg.result, msg.postconditions, context)
            mesh.publish(newEvent(correlationId, e, "verifier", MicroAgentTopics.VERIFICATION_COMPLETED, MicroAgentMessageCodec.encode(VerificationResult(verdict)), e.hopCount + 1))
        }
        mesh.subscribe("orch-verified", MicroAgentTopics.VERIFICATION_COMPLETED) { e ->
            if (mesh.isCancelled(e.correlationId)) return@subscribe publishTerminal(correlationId, e, "cancelled", "orchestration cancelled", null, null)
            val v = MicroAgentMessageCodec.decode(e.payloadString()) as VerificationResult
            val verified = v.result is PostconditionResult.Passed
            // D10: final attachment resolution happens between verification and terminal.
            val attachment = attachmentResolver(context.conversationId)
            if (verified) {
                publishTerminal(correlationId, e, "completed", "orchestration completed", v.result, attachment)
            } else {
                publishTerminal(correlationId, e, "failed", "postcondition failed", v.result, attachment)
            }
        }
        mesh.subscribe("orch-cancelled", MicroAgentTopics.ORCHESTRATION_CANCELLED) { e ->
            publishTerminal(correlationId, e, "cancelled", "orchestration cancelled", null, null)
        }

        // D7: cancellation through correlationId. A cancelled correlation stops delivery to
        // subscribers, so a watcher polls the cancellation flag and emits the single terminal
        // ORCHESTRATION_CANCELLED event (which the sink still records), unblocking the caller.
        scope.launch {
            while (!terminalPublished.contains(correlationId)) {
                if (mesh.isCancelled(correlationId)) {
                    publishTerminal(correlationId, null, "cancelled", "orchestration cancelled", null, null)
                    break
                }
                delay(5)
            }
        }

        mesh.publish(newEvent(correlationId, null, "orchestrator", MicroAgentTopics.PLAN_REQUESTED, MicroAgentMessageCodec.encode(PlanRequested(request)), 0))
        return completion.receive()
    }

    private fun newEvent(correlationId: String, cause: MicroAgentEvent?, source: String, topic: String, payload: String, hop: Int): MicroAgentEvent =
        MicroAgentEvent(
            sourceAgentId = source,
            topic = topic,
            payload = buildJsonObject { put("message", JsonPrimitive(payload)) },
            eventId = idFactory(),
            correlationId = correlationId,
            causationId = cause?.eventId,
            hopCount = hop,
            maxHops = 8,
        )

    /**
     * Publishes the single terminal orchestration event for [correlationId] and hands
     * the result back to the caller. Terminal topic is chosen from
     * ORCHESTRATION_COMPLETED / FAILED / CANCELLED. At most one terminal event is ever
     * published per correlation; a late event (e.g. a verification arriving after
     * cancellation) is suppressed and cannot replace the terminal state.
     */
    private suspend fun publishTerminal(
        correlationId: String,
        cause: MicroAgentEvent?,
        kind: String, // "completed" | "failed" | "cancelled" | "rejected"
        summary: String,
        postcondition: PostconditionResult?,
        attachment: String?,
    ) {
        if (!terminalPublished.add(correlationId)) return // already terminal; suppress late events
        val terminalTopic = when (kind) {
            "cancelled" -> MicroAgentTopics.ORCHESTRATION_CANCELLED
            "rejected", "failed" -> MicroAgentTopics.ORCHESTRATION_FAILED
            else -> MicroAgentTopics.ORCHESTRATION_COMPLETED
        }
        val status = if (kind == "cancelled") "cancelled" else if (kind == "rejected" || kind == "failed") "failed" else "completed"
        val message = OrchestrationResult(
            status = status,
            summary = summary,
            result = null,
            postcondition = postcondition,
        )
        mesh.publish(newEvent(correlationId, cause, "orchestrator", terminalTopic, MicroAgentMessageCodec.encode(message), (cause?.hopCount ?: 0) + 1))
        completion.trySend(message)
    }

    private fun MicroAgentEvent.payloadString(): String = (payload["message"] as? JsonPrimitive)?.content ?: ""
}
