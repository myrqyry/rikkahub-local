package me.rerere.rikkahub.data.agentrun

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.locallm.litert.mesh.MeshPublishResult
import me.rerere.locallm.litert.mesh.MicroAgentEvent
import me.rerere.locallm.litert.mesh.MicroAgentEventSink

/**
 * Phase D8 — persists the micro-agent event mesh into the AgentRun ledger as append-only
 * evidence. The sink opens a run on the first event for a correlation id, records every
 * published/delivered/rejected event with its envelope metadata, and marks the run
 * terminal on the first ORCHESTRATION_* event. Payloads are sanitized so full model
 * prompts / internal chain-of-thought never enter the ledger.
 *
 * Best-effort: tracing must never tear down orchestration.
 */
class AgentRunMicroAgentEventSink(
    private val repository: AgentRunRepository,
    private val trace: AgentRunTraceRepository,
    private val sanitizer: TracePayloadSanitizer = TracePayloadSanitizer(),
) : MicroAgentEventSink {

    private val runIdsByCorrelation = HashMap<String, String>()
    private val terminalCorrelations = HashSet<String>()

    override suspend fun publish(event: MicroAgentEvent) {
        runCatching {
            val correlationId = event.correlationId
            if (correlationId in terminalCorrelations) return

            val runId = runIdsByCorrelation.getOrPut(correlationId) {
                repository.open(
                    kind = AgentRunKind.Workflow,
                    domainId = correlationId,
                    metadata = buildJsonObject {
                        put("kind", "micro_agent_orchestration")
                        put("source_agent", event.sourceAgentId)
                    },
                )
            }

            val envelope = buildJsonObject {
                put("event_id", event.eventId)
                put("correlation_id", event.correlationId)
                event.causationId?.let { put("causation_id", it) }
                event.runId?.let { put("run_id", it) }
                put("source_agent", event.sourceAgentId)
                put("topic", event.topic)
                put("hop_count", event.hopCount)
                event.deadlineAtMs?.let { put("deadline_at_ms", it) }
                if (event.payload.isNotEmpty()) put("message_payload", event.payload.toString())
            }

            val sanitized = sanitizer.sanitize(null, envelope.toString())
            trace.append(
                runId,
                NewAgentRunEvent(
                    type = AgentRunEventType.AGENT_EVENT_PUBLISHED,
                    summary = "[${event.sourceAgentId}] ${event.topic}",
                    operationId = event.correlationId,
                    payloadJson = sanitized.payloadJson,
                ),
            )

            when (event.topic) {
                "orchestration.completed", "orchestration.failed", "orchestration.cancelled" -> {
                    terminalCorrelations += correlationId
                    val type = when (event.topic) {
                        "orchestration.completed" -> AgentRunEventType.ORCHESTRATION_COMPLETED
                        "orchestration.failed" -> AgentRunEventType.ORCHESTRATION_FAILED
                        else -> AgentRunEventType.ORCHESTRATION_CANCELLED
                    }
                    trace.append(
                        runId,
                        NewAgentRunEvent(
                            type = type,
                            severity = if (type == AgentRunEventType.ORCHESTRATION_COMPLETED) TraceSeverity.INFO else TraceSeverity.ERROR,
                            summary = "orchestration ${event.topic} (${event.correlationId})",
                            operationId = event.correlationId,
                        ),
                    )
                    repository.markTerminal(
                        runId,
                        if (type == AgentRunEventType.ORCHESTRATION_COMPLETED) AgentRunStatus.succeeded else AgentRunStatus.failed,
                    )
                }
            }
        }
    }
}
