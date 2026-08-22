package me.rerere.rikkahub.data.agentrun

import me.rerere.agentruntime.EvidenceQuery
import me.rerere.agentruntime.EvidenceRecord
import me.rerere.agentruntime.EvidenceStore
import me.rerere.agentruntime.EvidenceWriteResult
import me.rerere.agentruntime.ProvenanceAnchor
import me.rerere.rikkahub.data.db.entity.EvidenceDao
import me.rerere.rikkahub.data.db.entity.EvidenceEntity

class RoomEvidenceStore(
    private val dao: EvidenceDao,
) : EvidenceStore {
    override fun put(record: EvidenceRecord): EvidenceWriteResult {
        if (dao.insert(record.toEntity()) != -1L) return EvidenceWriteResult.Stored
        return EvidenceWriteResult.Duplicate(checkNotNull(dao.getById(record.id)).toRecord())
    }

    override fun get(id: String): EvidenceRecord? = dao.getById(id)?.toRecord()

    override fun query(query: EvidenceQuery): List<EvidenceRecord> =
        dao.query(query.type, query.origin, query.sessionId).map(EvidenceEntity::toRecord)
}

private fun EvidenceRecord.toEntity() = EvidenceEntity(
    id = id,
    type = type,
    payload = payload,
    origin = provenance.origin,
    sessionId = provenance.sessionId,
)

private fun EvidenceEntity.toRecord() = EvidenceRecord(
    id = id,
    type = type,
    payload = payload,
    provenance = ProvenanceAnchor(origin = origin, sessionId = sessionId),
)
