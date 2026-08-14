package me.rerere.rikkahub.data.ai.revision

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationRevisionGuardTest {
    @Test
    fun exactSnapshotMatches() = runBlocking {
        val snapshot = ConversationSnapshot("conversation", "branch-a", 3)
        val guard = ConversationRevisionGuard(object : ConversationRevisionSource {
            override suspend fun currentState(conversationId: String) = snapshot
        })

        assertEquals(RevisionCheckResult.Match, guard.check(snapshot))
    }

    @Test
    fun regressedRevisionIsReportedSeparately() = runBlocking {
        val expected = ConversationSnapshot("conversation", "branch-a", 3)
        val guard = ConversationRevisionGuard(object : ConversationRevisionSource {
            override suspend fun currentState(conversationId: String) =
                ConversationSnapshot(conversationId, "branch-a", 2)
        })

        assertEquals(
            RevisionCheckResult.RevisionRegressed(3, 2),
            guard.check(expected),
        )
    }

    @Test
    fun changedBranchIsNotReducedToRevisionDrift() = runBlocking {
        val expected = ConversationSnapshot("conversation", "branch-a", 3)
        val guard = ConversationRevisionGuard(object : ConversationRevisionSource {
            override suspend fun currentState(conversationId: String) =
                ConversationSnapshot(conversationId, "branch-b", 4)
        })

        assertEquals(
            RevisionCheckResult.BranchChanged("branch-a", "branch-b"),
            guard.check(expected),
        )
    }
}
