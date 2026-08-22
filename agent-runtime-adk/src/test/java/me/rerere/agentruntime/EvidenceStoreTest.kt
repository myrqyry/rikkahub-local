package me.rerere.agentruntime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvidenceStoreTest {

    private fun store(): EvidenceStore = InMemoryEvidenceStore()

    private fun record(
        id: String,
        type: String = "trajectory",
        origin: String = "meristem",
        sessionId: String = "run-1",
    ) = EvidenceRecord(id, type, "payload-$id", ProvenanceAnchor(origin, sessionId))

    @Test
    fun `stores and retrieves evidence`() = runBlocking {
        val record = record("rec-1")
        val store = store()

        assertEquals(EvidenceWriteResult.Stored, store.put(record))
        assertEquals(record, store.get(record.id))
        assertNull(store.get("missing"))
    }

    @Test
    fun `duplicate ids preserve the original record`() = runBlocking {
        val original = record("rec-1", type = "trajectory")
        val replacement = record("rec-1", type = "evaluation")
        val store = store()

        assertEquals(EvidenceWriteResult.Stored, store.put(original))
        assertEquals(EvidenceWriteResult.Duplicate(original), store.put(replacement))
        assertEquals(original, store.get(original.id))
    }

    @Test
    fun `queries conjunctively by type origin and session`() = runBlocking {
        val store = store()
        val matching = record("match", origin = "claude", sessionId = "s1")
        store.put(matching)
        store.put(record("wrong-type", type = "evaluation", origin = "claude", sessionId = "s1"))
        store.put(record("wrong-origin", origin = "codex", sessionId = "s1"))
        store.put(record("wrong-session", origin = "claude", sessionId = "s2"))

        assertEquals(
            listOf(matching),
            store.query(EvidenceQuery(type = "trajectory", origin = "claude", sessionId = "s1")),
        )
    }

    @Test
    fun `queries records in storage order`() = runBlocking {
        val store = store()
        store.put(record("first"))
        store.put(record("second"))

        assertEquals(listOf("first", "second"), store.query().map { it.id })
    }
}
