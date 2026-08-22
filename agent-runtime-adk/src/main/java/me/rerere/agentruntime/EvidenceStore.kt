package me.rerere.agentruntime

/** Records where a piece of evidence originated. */
data class ProvenanceAnchor(
    val origin: String,
    val sessionId: String,
)

/** Immutable evidence retained for an agent run. */
data class EvidenceRecord(
    val id: String,
    val type: String,
    val payload: String,
    val provenance: ProvenanceAnchor,
)

/** Optional filters applied together when querying evidence. */
data class EvidenceQuery(
    val type: String? = null,
    val origin: String? = null,
    val sessionId: String? = null,
)

sealed interface EvidenceWriteResult {
    data object Stored : EvidenceWriteResult

    data class Duplicate(
        val existing: EvidenceRecord,
    ) : EvidenceWriteResult
}

interface EvidenceStore {
    suspend fun put(record: EvidenceRecord): EvidenceWriteResult

    suspend fun get(id: String): EvidenceRecord?

    suspend fun query(query: EvidenceQuery = EvidenceQuery()): List<EvidenceRecord>
}

class InMemoryEvidenceStore : EvidenceStore {
    private val records = LinkedHashMap<String, EvidenceRecord>()

    override suspend fun put(record: EvidenceRecord): EvidenceWriteResult {
        records[record.id]?.let { return EvidenceWriteResult.Duplicate(it) }
        records[record.id] = record
        return EvidenceWriteResult.Stored
    }

    override suspend fun get(id: String): EvidenceRecord? = records[id]

    override suspend fun query(query: EvidenceQuery): List<EvidenceRecord> = records.values.filter { record ->
        (query.type == null || record.type == query.type) &&
            (query.origin == null || record.provenance.origin == query.origin) &&
            (query.sessionId == null || record.provenance.sessionId == query.sessionId)
    }
}
