package me.rerere.rikkahub.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import me.rerere.agentruntime.DeterministicToolResultContextPolicy
import me.rerere.agentruntime.EvidenceRecord
import me.rerere.agentruntime.EvidenceStore
import me.rerere.agentruntime.EvidenceWriteResult
import me.rerere.agentruntime.ProvenanceAnchor
import me.rerere.agentruntime.ToolResultContextCandidate
import me.rerere.agentruntime.ToolResultContextPolicy
import me.rerere.agentruntime.ToolResultContextProcessor
import me.rerere.agentruntime.ToolResultDisposition
import me.rerere.ai.ui.UIMessagePart

private const val TAG = "ToolResultContextProjector"
private const val DEFAULT_MAX_INLINE_BYTES = 32 * 1024

class ToolResultContextProjector(
    private val evidenceStore: EvidenceStore,
    private val policy: ToolResultContextPolicy =
        DeterministicToolResultContextPolicy(DEFAULT_MAX_INLINE_BYTES),
    private val maxInlineBytes: Int = DEFAULT_MAX_INLINE_BYTES,
) {
    suspend fun project(
        toolName: String,
        toolCallId: String,
        conversationId: String?,
        output: List<UIMessagePart>,
    ): List<UIMessagePart> {
        val textParts = output.filterIsInstance<UIMessagePart.Text>()
        if (textParts.isEmpty()) return output

        val text = textParts.joinToString("\n") { it.text }
        val sessionId = conversationId ?: "unbound"
        val evidenceId = "tool:$sessionId:$toolCallId"
        val persistedEvidenceId = try {
            when (evidenceStore.put(
                EvidenceRecord(
                    id = evidenceId,
                    type = "tool_result_text",
                    payload = text,
                    provenance = ProvenanceAnchor(
                        origin = "tool:$toolName",
                        sessionId = sessionId,
                    ),
                ),
            )) {
                EvidenceWriteResult.Stored,
                is EvidenceWriteResult.Duplicate,
                    -> evidenceId
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            runCatching { Log.w(TAG, "failed to preserve $toolName result", error) }
            null
        }
        val candidate = ToolResultContextCandidate(
            toolName = toolName,
            contentType = "text/plain",
            byteSize = text.toByteArray(Charsets.UTF_8).size,
            structured = false,
            evidenceId = persistedEvidenceId,
            exactRetrievable = false,
        )
        val requested = if (persistedEvidenceId == null) ToolResultDisposition.REDUCE else policy.decide(candidate)
        val projection = ToolResultContextProcessor.project(
            requested = requested,
            text = text,
            candidate = candidate,
            maxInlineBytes = maxInlineBytes,
        )
        val nonTextParts = output.filter { it !is UIMessagePart.Text }
        return projection.text?.let { listOf(UIMessagePart.Text(it)) + nonTextParts } ?: nonTextParts
    }
}
