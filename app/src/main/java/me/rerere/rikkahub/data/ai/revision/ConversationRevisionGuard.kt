package me.rerere.rikkahub.data.ai.revision

/** Conversation state observed when asynchronous work starts. */
data class ConversationSnapshot(
    val conversationId: String,
    val branchId: String,
    val revision: Long,
)

enum class RevisionCommitPolicy {
    REQUIRE_EXACT_MATCH,
    ALLOW_DESCENDANT_REVISION,
    ATTACH_AS_BACKGROUND_RESULT,
}

sealed interface RevisionCheckResult {
    data object Match : RevisionCheckResult

    data class ConversationMissing(
        val conversationId: String,
    ) : RevisionCheckResult

    data class BranchChanged(
        val expectedBranchId: String,
        val currentBranchId: String,
    ) : RevisionCheckResult

    data class RevisionAdvanced(
        val expected: Long,
        val actual: Long,
    ) : RevisionCheckResult
}

interface ConversationRevisionSource {
    suspend fun currentState(conversationId: String): ConversationSnapshot?
}

class ConversationRevisionGuard(
    private val source: ConversationRevisionSource,
) {
    suspend fun check(
        snapshot: ConversationSnapshot,
        policy: RevisionCommitPolicy = RevisionCommitPolicy.REQUIRE_EXACT_MATCH,
    ): RevisionCheckResult {
        val current = source.currentState(snapshot.conversationId)
            ?: return RevisionCheckResult.ConversationMissing(snapshot.conversationId)
        if (current.branchId != snapshot.branchId) {
            return RevisionCheckResult.BranchChanged(snapshot.branchId, current.branchId)
        }
        if (current.revision == snapshot.revision) {
            return RevisionCheckResult.Match
        }
        if (policy == RevisionCommitPolicy.ALLOW_DESCENDANT_REVISION && current.revision > snapshot.revision) {
            return RevisionCheckResult.Match
        }
        return RevisionCheckResult.RevisionAdvanced(snapshot.revision, current.revision)
    }
}
