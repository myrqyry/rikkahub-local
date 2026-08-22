package me.rerere.agentruntime

data class EvidenceRetrievalProjection(
    val evidenceId: String,
    val found: Boolean,
    val disposition: ToolResultDisposition?,
    val text: String?,
)

class EvidenceRetrievalProjector(
    private val evidenceStore: EvidenceStore,
    private val policy: ToolResultContextPolicy = DeterministicToolResultContextPolicy(
        maxInlineBytes = DEFAULT_MAX_INLINE_BYTES,
    ),
    private val maxInlineBytes: Int = DEFAULT_MAX_INLINE_BYTES,
) {
    suspend fun projectEvidence(ids: List<String>): List<EvidenceRetrievalProjection> =
        ids.map { id ->
            evidenceStore.get(id)?.let(::projectRecord) ?: EvidenceRetrievalProjection(
                evidenceId = id,
                found = false,
                disposition = null,
                text = null,
            )
        }

    fun projectRecords(records: List<EvidenceRecord>): List<EvidenceRetrievalProjection> =
        records.map(::projectRecord)

    private fun projectRecord(record: EvidenceRecord): EvidenceRetrievalProjection {
        val candidate = ToolResultContextCandidate(
            toolName = record.provenance.origin,
            contentType = record.type,
            byteSize = record.payload.toByteArray(Charsets.UTF_8).size,
            structured = false,
            evidenceId = record.id,
            exactRetrievable = false,
        )
        val projection = ToolResultContextProcessor.project(
            requested = policy.decide(candidate),
            text = record.payload,
            candidate = candidate,
            maxInlineBytes = maxInlineBytes,
        )
        return EvidenceRetrievalProjection(
            evidenceId = record.id,
            found = true,
            disposition = projection.disposition,
            text = projection.text,
        )
    }

    private companion object {
        const val DEFAULT_MAX_INLINE_BYTES = 32 * 1024
    }
}
