package me.rerere.agentruntime

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ChangedFile(
    val path: String,
    val kind: ChangeKind,
)

@Serializable
enum class ChangeKind {
    Added,
    Modified,
    Deleted,
}

/** A command result supplied by the execution owner. This type does not run commands. */
@Serializable
data class VerificationResult(
    val command: String,
    val exitCode: Int?,
    val output: String = "",
    val durationMs: Long,
)

@Serializable
data class ReceiptArtifact(
    val path: String,
    val kind: ArtifactKind,
)

@Serializable
enum class ArtifactKind {
    Receipt,
    Other,
}

@Serializable
data class VerificationSummary(
    val command: String,
    val exitCode: Int?,
    val outputTail: String,
    val durationMs: Long,
)

@Serializable
data class WorkReceipt(
    val taskId: String,
    val claim: String,
    val changedFiles: List<ChangedFile>,
    val checks: List<VerificationSummary>,
    val artifacts: List<ReceiptArtifact>,
    val unresolvedItems: List<String>,
    val createdAtMs: Long,
)

data class WorkReceiptInput(
    val taskId: String,
    val claim: String,
    val changedFiles: List<ChangedFile> = emptyList(),
    val checks: List<VerificationResult> = emptyList(),
    val artifacts: List<ReceiptArtifact> = emptyList(),
    val unresolvedItems: List<String> = emptyList(),
)

/** Builds a receipt from observed facts and stores each raw check as evidence. */
class WorkReceiptCollector(
    private val evidenceStore: EvidenceStore,
    private val maxOutputChars: Int = DEFAULT_OUTPUT_CHARS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun collect(input: WorkReceiptInput): WorkReceipt {
        require(maxOutputChars > 0) { "maxOutputChars must be positive" }

        val summaries = input.checks.mapIndexed { index, result ->
            evidenceStore.put(
                EvidenceRecord(
                    id = "${input.taskId}:check:$index",
                    type = EVIDENCE_TYPE,
                    payload = Json.encodeToString(result),
                    provenance = ProvenanceAnchor(origin = "verification", sessionId = input.taskId),
                ),
            )
            VerificationSummary(
                command = result.command,
                exitCode = result.exitCode,
                outputTail = result.output.takeLast(maxOutputChars),
                durationMs = result.durationMs,
            )
        }

        return WorkReceipt(
            taskId = input.taskId,
            claim = input.claim,
            changedFiles = input.changedFiles,
            checks = summaries,
            artifacts = input.artifacts,
            unresolvedItems = input.unresolvedItems,
            createdAtMs = nowMs(),
        )
    }

    companion object {
        const val EVIDENCE_TYPE = "verification_command_result"
        private const val DEFAULT_OUTPUT_CHARS = 4_000
    }
}
