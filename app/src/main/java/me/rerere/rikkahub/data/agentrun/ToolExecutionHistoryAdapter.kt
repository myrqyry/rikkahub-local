package me.rerere.rikkahub.data.agentrun

import kotlinx.serialization.json.buildJsonObject
import me.rerere.locallm.litert.zero.ToolExecution

/**
 * Roadmap B7 — turns the append-only [AgentRunEvent] trace into the normalized
 * [ToolExecution] history that [me.rerere.locallm.litert.zero.ProcedureMiner] consumes.
 *
 * Reads authoritative persisted history (across process restarts) rather than only the
 * in-memory receipts a live session happened to observe. Only successfully completed tool
 * steps are surfaced — a mined procedure is only meaningful for sequences that actually
 * completed.
 */
class ToolExecutionHistoryAdapter(
    private val trace: AgentRunTraceRepository,
) {
    suspend fun load(limit: Int = 500): List<ToolExecution> {
        return trace.successfulToolHistory(limit).mapNotNull { event ->
            val toolName = event.toolName ?: return@mapNotNull null
            ToolExecution(toolName = toolName, args = buildJsonObject { }, atMs = event.createdAtMs)
        }
    }
}
