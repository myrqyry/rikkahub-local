package me.rerere.rikkahub.data.agentrun

import kotlinx.serialization.json.JsonObject
import me.rerere.locallm.litert.WorkflowReceipt
import me.rerere.locallm.litert.zero.ProcedureMiner
import me.rerere.locallm.litert.zero.ToolExecution

/**
 * Roadmap B7 — wires procedure mining into the real execution history.
 *
 * Collects successful tool [WorkflowReceipt]s (ordered) into an in-memory
 * [ToolExecution] history, and after [executionsBeforeMine] successful executions
 * have been seen, runs [ProcedureMiner] and persists every mined procedure as a
 * disabled candidate through [ZeroProcedureRepository.putMined] (roadmap B8: mined
 * procedures are never auto-activated).
 *
 * Only receipts of kind "tool" with status "succeeded" are fed in — a mined
 * procedure is only meaningful for sequences that actually completed.
 */
class ProcedureMiningFeed(
    private val repository: ZeroProcedureRepository,
    private val miner: ProcedureMiner = ProcedureMiner(),
    private val executionsBeforeMine: Int = 20,
) {
    private val history = ArrayList<ToolExecution>()

    /** Record one successful tool execution; triggers a mining pass once the threshold is hit. */
    suspend fun record(receipt: WorkflowReceipt) {
        if (receipt.kind != "tool" || receipt.status != "succeeded") return
        history += ToolExecution(toolName = receipt.domainId, args = argsOf(receipt), atMs = receipt.requestedAtMs)
        if (history.size >= executionsBeforeMine) {
            mineAndReset()
        }
    }

    /** Run a mining pass over the collected history and persist candidates. */
    suspend fun mineAndReset() {
        if (history.isEmpty()) return
        val result = miner.mine(history.toList())
        for (mined in result.mined) {
            repository.putMined(mined.procedure, mined.support)
        }
        history.clear()
    }

    private fun argsOf(receipt: WorkflowReceipt): JsonObject {
        // ponytail: tool args are not surfaced on the receipt (only identity + outcome),
        // so mined steps carry empty args; the template resolver still routes by tool name.
        return kotlinx.serialization.json.buildJsonObject { }
    }
}
