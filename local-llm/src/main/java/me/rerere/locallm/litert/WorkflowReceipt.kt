package me.rerere.locallm.litert

import kotlinx.serialization.Serializable

/**
 * Phase 4 — immutable, serialisable audit record of one [ActionPlan] execution.
 *
 * Produced by [ActionPlanExecutor] for EVERY execution attempt, regardless of outcome:
 * a compile-invalid plan still produces a receipt (so a missing tool or a bad argument is
 * visible in the audit trail), as does a capability rejection and a workflow that never
 * fired because no [ZeroWorkflowExecutor] was wired.
 *
 * The receipt is deliberately field-for-field plain data (no lambdas, no references to
 * Rikka's live objects) so it can cross module boundaries and be persisted by a
 * [WorkflowReceiptSink] implementation living in `app` (e.g. the AgentRun ledger).
 */
@Serializable
data class WorkflowReceipt(
    /** Unique id for this execution. */
    val receiptId: String,
    /** "tool" for [ActionPlan.ToolCall], "workflow" for [ActionPlan.WorkflowCall]. */
    val kind: String,
    /** The tool name (tool kind) or workflow id (workflow kind). */
    val domainId: String,
    val requestedAtMs: Long,
    /** "valid" | "repaired" | "invalid" — the [CompilationResult] branch taken. */
    val compileOutcome: String,
    /** Human-readable repair summaries, present when [compileOutcome] == "repaired". */
    val repairs: List<String> = emptyList(),
    /** Human-readable diagnostics, present when [compileOutcome] == "invalid". */
    val diagnostics: List<String> = emptyList(),
    /** The capabilities the plan was allowed to exercise. */
    val grantedCapabilities: List<String> = emptyList(),
    /** "succeeded" | "failed" | "capability_rejected". */
    val status: String,
    val errorMessage: String? = null,
    val durationMs: Long,
)

/**
 * Phase 4 — seam through which [ActionPlanExecutor] emits audit receipts.
 *
 * The sink is nullable and lives behind an interface because `local-llm` must not depend on
 * the app-side ledger ([AgentRunRepository]); the app wires a concrete implementation via DI.
 * A null sink is always safe: the executor's core contract is unaffected, only observability
 * is lost.
 */
fun interface WorkflowReceiptSink {
    suspend fun record(receipt: WorkflowReceipt)
}
