package me.rerere.agentruntime

/**
 * Evidence archive with provenance anchors, harvested from AgentsView.
 *
 * AgentsView imports external agent sessions into a local archive and
 * re-prefixes imported session IDs by origin to avoid collisions
 * (`prefixImportedSessionID(origin, id)`). Meristem's [EvidenceArchive]
 * stores already-well-defined evidence — trajectory records (Tunix), action
 * invocations, evaluation results (Langfun), and external session evidence —
 * each wrapped in a [EvidenceRecord] envelope with a [ProvenanceAnchor].
 *
 * The small trajectory primitive (`TrajectoryRecorder.RecordedStep`) stays
 * lean; evidence attaches here via the envelope, never by fattening the
 * trajectory type.
 */

/** Where a session or evidence record came from — the provenance anchor. */
data class ProvenanceAnchor(
    val origin: String,
    val sessionId: String,
)

/** The archival envelope: type-tagged payload plus provenance. */
data class EvidenceRecord(
    val id: String,
    val type: String,
    val payload: String,
    val provenance: ProvenanceAnchor,
)

/** In-memory durable store for evidence records, ordered by insertion. */
class EvidenceArchive {
    private val records = LinkedHashMap<String, EvidenceRecord>()

    /** Stores a record by id, replacing any prior record with the same id. */
    fun put(record: EvidenceRecord) {
        records[record.id] = record
    }

    /** Returns the record for [id], or null if absent. */
    fun get(id: String): EvidenceRecord? = records[id]

    /** Returns records filtered by type and/or origin, in insertion order. */
    fun query(type: String? = null, origin: String? = null): List<EvidenceRecord> =
        records.values.filter { record ->
            (type == null || record.type == type) &&
                (origin == null || record.provenance.origin == origin)
        }
}
