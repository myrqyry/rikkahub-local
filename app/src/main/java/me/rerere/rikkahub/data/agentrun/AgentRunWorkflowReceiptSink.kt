package me.rerere.rikkahub.data.agentrun

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.locallm.litert.WorkflowReceipt
import me.rerere.locallm.litert.WorkflowReceiptSink

/**
 * Phase 4 — app-side adapter that writes every [WorkflowReceipt] produced by the LiteRT
 * execution path into the unified [AgentRunRepository] ledger, so automatic tool/workflow
 * executions are visible in the same audit trail as cron jobs and sub-agents.
 *
 * Mapping:
 *  - receipt kind "tool"      → [AgentRunKind.ExternalAutomation] (the tool call is an
 *    external automation fired by the local model, not a user-authored workflow)
 *  - receipt kind "workflow"  → [AgentRunKind.Workflow]
 *  - receipt status "succeeded" → [AgentRunStatus.succeeded], everything else → [AgentRunStatus.failed]
 *
 * A receipt is recorded as a single open→markTerminal pair: the ledger row is created and
 * immediately closed in one call. `last_error` is populated from the receipt's error
 * message, or a `plan_compile_invalid` marker when the plan never passed the compiler.
 *
 * The repository is best-effort by design, so a ledger write failure never propagates into
 * the tool execution path.
 */
class AgentRunWorkflowReceiptSink(
    private val repository: AgentRunRepository,
    private val miningFeed: ProcedureMiningFeed? = null,
) : WorkflowReceiptSink {

    override suspend fun record(receipt: WorkflowReceipt) {
        // Roadmap B7 — feed successful tool executions into procedure mining.
        miningFeed?.record(receipt)

        val kind = if (receipt.kind == "workflow") AgentRunKind.Workflow else AgentRunKind.ExternalAutomation
        val status = if (receipt.status == "succeeded") AgentRunStatus.succeeded else AgentRunStatus.failed
        val metadata: JsonObject = buildJsonObject {
            put("receipt_id", receipt.receiptId)
            put("requested_at_ms", receipt.requestedAtMs)
            put("compile_outcome", receipt.compileOutcome)
            put("duration_ms", receipt.durationMs)
            put("receipt_status", receipt.status)
        }
        val lastError = receipt.errorMessage
            ?: if (receipt.compileOutcome == "invalid") "plan_compile_invalid" else null
        val runId = repository.open(kind, receipt.domainId, metadata = metadata)
        repository.markTerminal(runId, status, lastError = lastError)
    }
}
