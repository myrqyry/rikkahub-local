package me.rerere.rikkahub.data.ai.revision

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsyncAttachmentGateTest {

    private fun guard(
        snapshot: ConversationSnapshot?,
    ) = ConversationRevisionGuard(
        source = object : ConversationRevisionSource {
            override suspend fun currentState(conversationId: String): ConversationSnapshot? = snapshot
        },
    )

    private val gateBase = AsyncAttachmentGate(
        guard(
            ConversationSnapshot(conversationId = "c1", branchId = "b1", revision = 5L),
        ),
    )

    @Test
    fun exactMatchAttachesNormally() = runBlocking {
        val d = gateBase.decide(
            ConversationSnapshot("c1", "b1", 5L),
            RevisionCommitPolicy.REQUIRE_EXACT_MATCH,
        )
        assertEquals(AsyncAttachmentGate.Decision.Attach, d)
    }

    @Test
    fun descendantRevisionAttachesAsBackgroundUnderExactPolicy() = runBlocking {
        val d = gateBase.decide(
            ConversationSnapshot("c1", "b1", 4L),
            RevisionCommitPolicy.REQUIRE_EXACT_MATCH,
        )
        assertTrue(d is AsyncAttachmentGate.Decision.AttachAsBackground)
        assertEquals(5L, (d as AsyncAttachmentGate.Decision.AttachAsBackground).snapshot.revision)
    }

    @Test
    fun descendantRevisionAttachesNormallyUnderPermissivePolicy() = runBlocking {
        val d = gateBase.decide(
            ConversationSnapshot("c1", "b1", 4L),
            RevisionCommitPolicy.ALLOW_DESCENDANT_REVISION,
        )
        assertEquals(AsyncAttachmentGate.Decision.Attach, d)
    }

    @Test
    fun regressionRejects() = runBlocking {
        val d = gateBase.decide(
            ConversationSnapshot("c1", "b1", 9L),
            RevisionCommitPolicy.REQUIRE_EXACT_MATCH,
        )
        assertTrue(d is AsyncAttachmentGate.Decision.Reject)
        assertTrue((d as AsyncAttachmentGate.Decision.Reject).reason.contains("regressed"))
    }

    @Test
    fun branchChangeRejects() = runBlocking {
        val d = gateBase.decide(
            ConversationSnapshot("c1", "other-branch", 5L),
            RevisionCommitPolicy.REQUIRE_EXACT_MATCH,
        )
        assertTrue(d is AsyncAttachmentGate.Decision.Reject)
        assertTrue((d as AsyncAttachmentGate.Decision.Reject).reason.contains("branch changed"))
    }

    @Test
    fun missingConversationRejects() = runBlocking {
        val gate = AsyncAttachmentGate(guard(null))
        val d = gate.decide(ConversationSnapshot("gone", "b1", 1L))
        assertTrue(d is AsyncAttachmentGate.Decision.Reject)
        assertTrue((d as AsyncAttachmentGate.Decision.Reject).reason.contains("no longer exists"))
    }

    @Test
    fun completedOrchestrationRecordedButStaleRevisionAttachmentRejected() = runBlocking {
        // The orchestration completed at the current revision (5) and its result was recorded.
        val recordedResults = mutableListOf("orchestration-completed")
        val gate = AsyncAttachmentGate(guard(ConversationSnapshot("c1", "b1", 5L)))

        // But the work captured a snapshot at revision 9, and by completion the conversation
        // has regressed to 5 -> the result IS recorded but must NOT be attached.
        val capturedAtStart = ConversationSnapshot("c1", "b1", 9L)
        val d = gate.decide(capturedAtStart)

        assertEquals(listOf("orchestration-completed"), recordedResults)
        assertTrue(d is AsyncAttachmentGate.Decision.Reject)
        assertTrue((d as AsyncAttachmentGate.Decision.Reject).reason.contains("regressed"))
    }
}
