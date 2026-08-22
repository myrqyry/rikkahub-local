package me.rerere.agentruntime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ContinuationStoreTest {

    @Test
    fun `append assigns increasing sequences per run`() = runBlocking {
        val store = InMemoryContinuationStore(now = { 100L })

        val first = store.append(draft("c1", "run-a"))
        val second = store.append(draft("c2", "run-a"))
        val other = store.append(draft("c3", "run-b"))

        assertEquals(1L, first.sequence)
        assertEquals(2L, second.sequence)
        assertEquals(1L, other.sequence)
        assertEquals(100L, first.createdAtMs)
    }

    @Test
    fun `duplicate IDs preserve the original checkpoint`() = runBlocking {
        val store = InMemoryContinuationStore()
        val original = draft("c1", "run-a", goal = "inspect")

        assertEquals(store.append(original), store.append(original.copy(snapshot = snapshot("replace"))))
    }

    @Test
    fun `latest and list stay scoped to one run`() = runBlocking {
        val store = InMemoryContinuationStore()
        store.append(draft("c1", "run-a"))
        val latest = store.append(draft("c2", "run-a"))
        store.append(draft("c3", "run-b"))

        assertEquals(latest, store.latest("run-a"))
        assertEquals(listOf("c1", "c2"), store.list("run-a").map { it.id })
    }

    @Test
    fun `append rejects an unverified checkpoint`() = runBlocking {
        try {
            InMemoryContinuationStore().append(draft("c1", "run-a").copy(verifiedAtMs = 0L))
            fail("unverified checkpoint must be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun draft(id: String, runId: String, goal: String = "continue") =
        ContinuationCheckpointDraft(
            id = id,
            runId = runId,
            verifiedAtMs = 10L,
            snapshot = snapshot(goal),
        )

    private fun snapshot(goal: String) = ContinuationSnapshot(
        goal = goal,
        pendingWork = "pending",
        lastVerifiedAction = "verified",
        verificationState = "passed",
    )
}
