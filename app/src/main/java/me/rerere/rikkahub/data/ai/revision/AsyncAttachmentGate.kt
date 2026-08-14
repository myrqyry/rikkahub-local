package me.rerere.rikkahub.data.ai.revision

/**
 * Roadmap C5: guard asynchronous completion / attachment.
 *
 * A long-running operation (model generation, long tool result, procedure
 * completion, artifact attachment, background agent) captures the conversation
 * state at work start. When it completes, the conversation may have advanced,
 * branched, or been deleted. This gate decides whether the completed result may
 * still be attached to that conversation.
 *
 * This is a separate invariant from KV-cache reuse (PromptPrefixKey): the prefix
 * hash asks "may I reuse this KV state?", while this gate asks "may I attach
 * this completed work here?".
 */
class AsyncAttachmentGate(private val guard: ConversationRevisionGuard) {

    sealed interface Decision {
        /** Conversation is unchanged; attach the completed result normally. */
        data object Attach : Decision

        /**
         * Conversation advanced past our snapshot; the result is still safe to
         * attach but should be surfaced as a background attachment.
         */
        data class AttachAsBackground(val snapshot: ConversationSnapshot) : Decision

        /** Attachment must be refused; surface only as a background note. */
        data class Reject(val reason: String, val snapshot: ConversationSnapshot) : Decision
    }

    /**
     * Decide how to handle a completed result for [capturedAtStart], the
     * conversation snapshot taken when the work began.
     *
     * @param policy how much revision drift is acceptable. Under
     *   REQUIRE_EXACT_MATCH any advance becomes a background attachment; under
     *   ALLOW_DESCENDANT_REVISION a forward revision still attaches normally. A
     *   branch change or regression always rejects.
     */
    suspend fun decide(
        capturedAtStart: ConversationSnapshot,
        policy: RevisionCommitPolicy = RevisionCommitPolicy.REQUIRE_EXACT_MATCH,
    ): Decision {
        val result = guard.check(capturedAtStart, policy)
        return when (result) {
            is RevisionCheckResult.Match -> Decision.Attach
            is RevisionCheckResult.RevisionAdvanced ->
                Decision.AttachAsBackground(capturedAtStart.copy(revision = result.actual))
            is RevisionCheckResult.ConversationMissing -> Decision.Reject(
                "conversation no longer exists",
                capturedAtStart,
            )
            is RevisionCheckResult.BranchChanged -> Decision.Reject(
                "branch changed from ${result.expectedBranchId} to ${result.currentBranchId}",
                capturedAtStart.copy(branchId = result.currentBranchId),
            )
            is RevisionCheckResult.RevisionRegressed -> Decision.Reject(
                "revision regressed from ${result.expected} to ${result.actual}",
                capturedAtStart.copy(revision = result.actual),
            )
        }
    }
}
