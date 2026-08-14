package me.rerere.rikkahub.data.agentrun

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.locallm.litert.PostconditionResult
import me.rerere.locallm.litert.zero.ProcedureStatus
import me.rerere.locallm.litert.zero.StepStatus
import me.rerere.locallm.litert.zero.ZeroProcedureReceipt
import me.rerere.locallm.litert.zero.ZeroProcedureReceiptSink

/**
 * Roadmap B6 — persists a first-class [ZeroProcedureReceipt] into the AgentRun event
 * ledger. Opens a run for the procedure, records every step independently, then marks
 * the run terminal. A run can therefore reconstruct exactly which steps succeeded and
 * why the last failed without inspecting model prose.
 */
class AgentRunProcedureReceiptSink(
    private val repository: AgentRunRepository,
    private val trace: AgentRunTraceRepository,
) : ZeroProcedureReceiptSink {

    override suspend fun record(receipt: ZeroProcedureReceipt, postcondition: PostconditionResult) {
        // best-effort: trace must never tear down procedure execution
        runCatching {
            val runId = repository.open(
                kind = AgentRunKind.Workflow,
                domainId = receipt.procedureId,
                metadata = buildJsonObject {
                    put("kind", "procedure")
                    put("procedure_revision", receipt.revision)
                    put("started_at_ms", receipt.startedAtMs)
                },
            )
            trace.append(
                runId,
                NewAgentRunEvent(
                    type = AgentRunEventType.PROCEDURE_STARTED,
                    summary = "procedure '${receipt.procedureId}' (rev ${receipt.revision}) started",
                    operationId = receipt.procedureId,
                ),
            )
            for (step in receipt.steps) {
                val type = when (step.status) {
                    StepStatus.SUCCEEDED -> AgentRunEventType.PROCEDURE_STEP_SUCCEEDED
                    StepStatus.FAILED -> AgentRunEventType.PROCEDURE_STEP_FAILED
                    StepStatus.TIMEOUT -> AgentRunEventType.PROCEDURE_STEP_TIMEOUT
                    StepStatus.SKIPPED -> AgentRunEventType.PROCEDURE_STEP_SKIPPED
                }
                val severity = if (step.status == StepStatus.SUCCEEDED) TraceSeverity.INFO else TraceSeverity.WARNING
                val payload = buildJsonObject {
                    put("procedure_id", receipt.procedureId)
                    put("procedure_revision", receipt.revision)
                    put("step_id", step.stepId)
                    put("tool_name", step.toolName)
                    put("step_status", step.status.name)
                    put("started_at_ms", step.startedAtMs)
                    put("completed_at_ms", step.completedAtMs)
                    put("input_digest", step.inputDigest)
                    step.output?.let { put("output", it.toString()) }
                    step.diagnostic?.let { put("diagnostic", "${it.code}: ${it.message}") }
                }
                trace.append(
                    runId,
                    NewAgentRunEvent(
                        type = type,
                        severity = severity,
                        summary = "step '${step.stepId}' (${step.toolName}): ${step.status.name}",
                        toolName = step.toolName,
                        operationId = step.stepId,
                        payloadJson = payload.toString(),
                    ),
                )
            }
            val success = receipt.status == ProcedureStatus.SUCCEEDED
            trace.append(
                runId,
                NewAgentRunEvent(
                    type = AgentRunEventType.PROCEDURE_COMPLETED,
                    severity = if (success) TraceSeverity.INFO else TraceSeverity.ERROR,
                    summary = "procedure '${receipt.procedureId}' ${receipt.status.name}",
                    operationId = receipt.procedureId,
                ),
            )
            when (postcondition) {
                is PostconditionResult.Passed -> trace.append(
                    runId,
                    NewAgentRunEvent(
                        type = AgentRunEventType.POSTCONDITION_VERIFIED,
                        summary = "procedure '${receipt.procedureId}' passed postconditions",
                        operationId = receipt.procedureId,
                    ),
                )
                is PostconditionResult.Failed -> trace.append(
                    runId,
                    NewAgentRunEvent(
                        type = AgentRunEventType.POSTCONDITION_FAILED,
                        severity = TraceSeverity.ERROR,
                        summary = "procedure '${receipt.procedureId}' postcondition failed: ${postcondition.code}",
                        operationId = receipt.procedureId,
                        payloadJson = buildJsonObject { put("detail", postcondition.detail) }.toString(),
                    ),
                )
            }
            repository.markTerminal(
                runId,
                if (success) AgentRunStatus.succeeded else AgentRunStatus.failed,
                lastError = receipt.error,
            )
        }
    }
}
