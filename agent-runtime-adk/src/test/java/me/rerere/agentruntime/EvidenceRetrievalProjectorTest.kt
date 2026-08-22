package me.rerere.agentruntime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvidenceRetrievalProjectorTest {

    @Test
    fun `explicit IDs preserve order and retain missing positions`() = runBlocking {
        val store = InMemoryEvidenceStore()
        store.put(record("first", "first payload"))
        store.put(record("third", "third payload"))
        val projector = EvidenceRetrievalProjector(store, policy = fixed(ToolResultDisposition.INLINE))

        val result = projector.projectEvidence(listOf("third", "missing", "first"))

        assertEquals(listOf("third", "missing", "first"), result.map { it.evidenceId })
        assertEquals("third payload", result[0].text)
        assertEquals(false, result[1].found)
        assertNull(result[1].disposition)
        assertNull(result[1].text)
        assertEquals("first payload", result[2].text)
    }

    @Test
    fun `projection reads source evidence without writing or mutating it`() = runBlocking {
        val store = RecordingEvidenceStore()
        val source = record("source", "payload")
        store.put(source)
        store.resetPutCalls()
        val projector = EvidenceRetrievalProjector(store, policy = fixed(ToolResultDisposition.INLINE))

        val result = projector.projectRecords(listOf(source))

        assertEquals("payload", result.single().text)
        assertEquals(source, store.get("source"))
        assertEquals(0, store.putCalls)
    }

    @Test
    fun `dispositions use the existing processor semantics`() = runBlocking {
        val source = record("evidence-1", "hello world")

        assertEquals("hello world", project(source, ToolResultDisposition.INLINE).text)
        assertEquals("hello...", project(source, ToolResultDisposition.REDUCE).text)
        assertEquals(
            ToolResultDisposition.REFERENCE_ONLY,
            project(source, ToolResultDisposition.INDEX_EXACT).disposition,
        )
        assertEquals(
            "[Tool result preserved as evidence: evidence-1]",
            project(source, ToolResultDisposition.REFERENCE_ONLY, maxInlineBytes = 80).text,
        )
        assertNull(project(source, ToolResultDisposition.DISCARD).text)
    }

    private suspend fun project(
        record: EvidenceRecord,
        disposition: ToolResultDisposition,
        maxInlineBytes: Int = 8,
    ): EvidenceRetrievalProjection = EvidenceRetrievalProjector(
        evidenceStore = InMemoryEvidenceStore().also { it.put(record) },
        policy = fixed(disposition),
        maxInlineBytes = maxInlineBytes,
    ).projectRecords(listOf(record)).single()

    private fun fixed(disposition: ToolResultDisposition) = ToolResultContextPolicy { disposition }

    private fun record(id: String, payload: String) = EvidenceRecord(
        id = id,
        type = "tool_result_text",
        payload = payload,
        provenance = ProvenanceAnchor(origin = "tool:shell", sessionId = "conversation-1"),
    )

    private class RecordingEvidenceStore : EvidenceStore {
        private val delegate = InMemoryEvidenceStore()
        var putCalls = 0
            private set

        override suspend fun put(record: EvidenceRecord): EvidenceWriteResult {
            putCalls++
            return delegate.put(record)
        }

        override suspend fun get(id: String): EvidenceRecord? = delegate.get(id)

        override suspend fun query(query: EvidenceQuery): List<EvidenceRecord> = delegate.query(query)

        fun resetPutCalls() {
            putCalls = 0
        }
    }
}
