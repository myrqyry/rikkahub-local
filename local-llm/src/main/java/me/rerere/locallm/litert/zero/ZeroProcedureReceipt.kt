package me.rerere.locallm.litert.zero

import kotlinx.serialization.Serializable
import me.rerere.locallm.litert.PostconditionResult

/**
 * First-class per-step evidence for a Zero procedure run (roadmap B6).
 * Each step is independently visible in the AgentRun ledger so the run can be
 * reconstructed exactly (which steps succeeded, why the third failed) without
 * inspecting model prose.
 */
@Serializable
data class ZeroStepReceipt(
    val procedureId: String,
    val procedureRevision: Long,
    val stepId: String,
    val toolName: String,
    val status: StepStatus,
    val startedAtMs: Long,
    val completedAtMs: Long,
    val inputDigest: String,
    val output: StepOutputRef? = null,
    val diagnostic: ZeroDiagnostic? = null,
)

enum class StepStatus {
    SUCCEEDED,
    FAILED,
    TIMEOUT,
    SKIPPED,
}

/** Aggregate evidence for a whole Zero procedure run (roadmap B6). */
@Serializable
data class ZeroProcedureReceipt(
    val procedureId: String,
    val revision: Long,
    val status: ProcedureStatus,
    val steps: List<ZeroStepReceipt>,
    val startedAtMs: Long,
    val completedAtMs: Long,
    val error: String? = null,
)

enum class ProcedureStatus {
    SUCCEEDED,
    FAILED,
}

/**
 * Fun-interface seam for persisting Zero procedure receipts. Null impl is safe;
 * the app wires a real impl via DI (mirrors WorkflowReceiptSink).
 */
fun interface ZeroProcedureReceiptSink {
    suspend fun record(receipt: ZeroProcedureReceipt, postcondition: PostconditionResult)
}
