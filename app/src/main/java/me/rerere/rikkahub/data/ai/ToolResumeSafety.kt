package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.CancellationException
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart

/**
 * When a mixed approval batch resumes, untouched Auto siblings from that same model
 * step must be processed alongside approved/denied/answered calls. Keep original
 * order, but never retry anything with output or an execution-start receipt.
 *
 * Started calls are handled by the replay-safety pass before this selection.
 * This predicate independently refuses to execute one if that pass was missed.
 */
internal fun resumableToolsIncludingUnexecutedAuto(
    tools: List<UIMessagePart.Tool>,
): List<UIMessagePart.Tool> = tools.filter { tool ->
    !tool.isExecuted && when (tool.approvalState) {
        is ToolApprovalState.Approved -> tool.executionStartedAt == null
        is ToolApprovalState.Denied, is ToolApprovalState.Answered -> true
        ToolApprovalState.Auto -> tool.executionStartedAt == null
        ToolApprovalState.Pending -> false
    }
}

/** Every started Auto/Approved side-effecting tool must write its marker before execution. */
internal fun shouldPersistToolStartReceipt(tools: List<UIMessagePart.Tool>): Boolean =
    tools.any { it.isInterruptedAttempt }

/** Avoid swallowing a user's stop inside runCatching.onFailure and forging a tool_failed result. */
internal fun propagateToolCancellation(error: Throwable) {
    if (error is CancellationException) throw error
}
