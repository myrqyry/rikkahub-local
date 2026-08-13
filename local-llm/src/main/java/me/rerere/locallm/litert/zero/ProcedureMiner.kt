package me.rerere.locallm.litert.zero

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Phase 9 — a single observed tool execution in authoring order.
 *
 * This is the pure mining input; an app-side adapter maps its execution history
 * (e.g. [me.rerere.locallm.litert.WorkflowReceipt]s of kind "tool" that succeeded)
 * into [ToolExecution]s. Only successful, ordered executions should be fed in —
 * a mined [ZeroProcedure] is only meaningful for sequences that actually completed.
 */
@Serializable
data class ToolExecution(
    val toolName: String,
    val args: JsonObject = buildJsonObject { },
    val atMs: Long = 0L,
)

/**
 * Phase 9 — a recurring tool-call sequence distilled into a runnable [ZeroProcedure].
 *
 * [procedure]'s steps carry the args observed on the first occurrence of the
 * sequence; [support] is how many times the sequence appeared.
 */
data class MinedProcedure(
    val procedure: ZeroProcedure,
    val support: Int,
    /** Length of the mined step sequence (= the number of steps in [procedure]). */
    val windowLength: Int,
    val firstSeenAtMs: Long,
    val lastSeenAtMs: Long,
)

/** Outcome of one [ProcedureMiner.mine] pass. */
data class MiningResult(
    val mined: List<MinedProcedure>,
    val totalExecutions: Int,
)

/**
 * Phase 9 — deterministic procedure mining for cached workflows.
 *
 * Scans the ordered [ToolExecution] history for tool-name sequences that recur at
 * least [minSupport] times with length in `[minSteps, maxSteps]`, prunes sequences
 * contained inside a longer frequent one (maximal frequent contiguous patterns),
 * and distills each surviving sequence into a [ZeroProcedure] whose steps execute
 * the same tool chain deterministically.
 *
 * Pure — never invokes a tool. Output is deterministic: same history in, same
 * procedures out, ordered by (support desc, window length desc, id asc).
 */
class ProcedureMiner(
    private val minSteps: Int = 3,
    private val minSupport: Int = 2,
    private val maxSteps: Int = 10,
) {

    /** One contiguous window `[start, end]` in the history where a sequence occurred. */
    private class WindowOcc(val start: Int, val end: Int)

    fun mine(history: List<ToolExecution>): MiningResult {
        val total = history.size
        if (total < minSteps) return MiningResult(emptyList(), total)

        // 1. Count every contiguous window of length [minSteps..maxSteps].
        val occurrences = LinkedHashMap<String, MutableList<WindowOcc>>()
        for (start in 0 until total) {
            val names = ArrayList<String>()
            val maxEnd = minOf(start + maxSteps, total)
            for (end in start until maxEnd) {
                names += history[end].toolName
                if (names.size < minSteps) continue
                val key = names.joinToString(SEPARATOR)
                occurrences.getOrPut(key) { mutableListOf() }.add(WindowOcc(start, end))
            }
        }

        // 2. Keep only frequent sequences, longest first.
        val frequent = occurrences.filterValues { it.size >= minSupport }
            .entries
            .sortedByDescending { it.value.first().let { w -> w.end - w.start + 1 } }

        // 3. Prune sequences that are a contiguous slice of an already-kept longer one.
        val kept = ArrayList<Pair<List<String>, List<WindowOcc>>>()
        for ((key, occs) in frequent) {
            val seq = key.split(SEPARATOR)
            if (kept.none { (longer, _) -> containsWindow(longer, seq) }) {
                kept.add(seq to occs)
            }
        }

        // 4. Deterministic order + distill into runnable procedures.
        val mined = kept
            .map { (seq, occs) -> toProcedure(history, seq, occs) }
            .sortedWith(
                compareByDescending<MinedProcedure> { it.support }
                    .thenByDescending { it.windowLength }
                    .thenBy { it.procedure.id },
            )
        return MiningResult(mined, total)
    }

    /** True when [shorter] appears as a contiguous slice of [longer]. */
    private fun containsWindow(longer: List<String>, shorter: List<String>): Boolean {
        if (shorter.size > longer.size) return false
        for (i in 0..(longer.size - shorter.size)) {
            var matches = true
            for (j in shorter.indices) {
                if (longer[i + j] != shorter[j]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }

    private fun toProcedure(
        history: List<ToolExecution>,
        seq: List<String>,
        occs: List<WindowOcc>,
    ): MinedProcedure {
        val first = occs.minBy { it.start }
        val steps = seq.mapIndexed { i, name ->
            ZeroStep(
                stepId = "s$i",
                tool = name,
                args = history.getOrNull(first.start + i)?.args ?: buildJsonObject { },
            )
        }
        // ponytail: id derived from the sequence itself so re-mining is stable;
        // namespaced "mined_" so it cannot collide with user-authored procedures.
        val id = "mined_" + seq.joinToString("_").replace(NON_ID_CHARS, "_")
        val last = occs.maxOf { it.end }
        return MinedProcedure(
            procedure = ZeroProcedure(
                id = id,
                description = "mined: " + seq.joinToString(" -> "),
                steps = steps,
                failFast = true,
            ),
            support = occs.size,
            windowLength = seq.size,
            firstSeenAtMs = history.getOrNull(first.start)?.atMs ?: 0L,
            lastSeenAtMs = history.getOrNull(last)?.atMs ?: 0L,
        )
    }

    private companion object {
        const val SEPARATOR = "\u0000"
        val NON_ID_CHARS = Regex("[^A-Za-z0-9_]")
    }
}
