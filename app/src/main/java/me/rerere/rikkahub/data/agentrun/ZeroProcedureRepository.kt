package me.rerere.rikkahub.data.agentrun

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.locallm.litert.zero.ProcedureCache
import me.rerere.locallm.litert.zero.ProcedureReviewState
import me.rerere.locallm.litert.zero.ZeroProcedure
import me.rerere.rikkahub.data.db.entity.ZeroProcedureDao
import me.rerere.rikkahub.data.db.entity.ZeroProcedureEntity

/** Authoring source of a persisted [ZeroProcedure], per roadmap B3. */
enum class ProcedureSource(val wire: String) {
    USER("USER"),
    SKILL("SKILL"),
    MINED("MINED"),
    GENERATED("GENERATED"),
}

/** Validation status projection for a stored procedure. */
enum class ProcedureValidationStatus(val wire: String) {
    PENDING("pending"),
    VALID("valid"),
    INVALID("invalid"),
}

/**
 * Room-backed [ProcedureCache] (roadmap B3). Stores each procedure's canonical JSON plus
 * projected metadata (source, enabled, revision, validation status, support count, timestamps).
 *
 * The JSON is the source of truth; rows are parsed on read through the shared [Json] instance.
 */
class ZeroProcedureRepository(
    private val dao: ZeroProcedureDao,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ProcedureCache {

    override suspend fun put(procedure: ZeroProcedure) {
        val now = System.currentTimeMillis()
        val existing = dao.getById(procedure.id)
        val entity = ZeroProcedureEntity(
            id = procedure.id,
            source = existing?.source ?: ProcedureSource.USER.wire,
            enabled = existing?.enabled ?: true,
            revision = (existing?.revision ?: 0L) + 1,
            validationStatus = existing?.validationStatus ?: ProcedureValidationStatus.PENDING.wire,
            supportCount = existing?.supportCount ?: 0,
            procedureJson = json.encodeToString(ZeroProcedure.serializer(), procedure),
            createdAtMs = existing?.createdAtMs ?: now,
            updatedAtMs = now,
        )
        dao.upsert(entity)
    }

    /**
     * Roadmap B7/B8 — persist a mined procedure as a *candidate*: source MINED,
     * disabled by default (never auto-activated), with the support count carried
     * from the miner. Re-mining the same sequence bumps support and revises the
     * stored procedure while preserving the MINED/disabled disposition.
     */
    suspend fun putMined(procedure: ZeroProcedure, support: Int) {
        val now = System.currentTimeMillis()
        val existing = dao.getById(procedure.id)
        val entity = ZeroProcedureEntity(
            id = procedure.id,
            source = ProcedureSource.MINED.wire,
            enabled = false,
            revision = (existing?.revision ?: 0L) + 1,
            validationStatus = existing?.validationStatus ?: ProcedureValidationStatus.PENDING.wire,
            supportCount = support,
            procedureJson = json.encodeToString(ZeroProcedure.serializer(), procedure),
            createdAtMs = existing?.createdAtMs ?: now,
            updatedAtMs = now,
        )
        dao.upsert(entity)
    }

    override suspend fun get(id: String): ZeroProcedure? {
        val row = dao.getById(id) ?: return null
        return runCatching { json.decodeFromString(ZeroProcedure.serializer(), row.procedureJson) }.getOrNull()
    }

    override suspend fun all(): List<ZeroProcedure> =
        dao.listAll().mapNotNull { row ->
            runCatching { json.decodeFromString(ZeroProcedure.serializer(), row.procedureJson) }.getOrNull()
        }

    suspend fun getEnabled(id: String): ZeroProcedure? {
        val row = dao.getById(id) ?: return null
        if (!row.enabled) return null
        return runCatching { json.decodeFromString(ZeroProcedure.serializer(), row.procedureJson) }.getOrNull()
    }

    suspend fun setEnabled(id: String, enabled: Boolean): Boolean {
        val row = dao.getById(id) ?: return false
        dao.update(row.copy(enabled = enabled, updatedAtMs = System.currentTimeMillis()))
        return true
    }

    /** Roadmap B8 — the stored row revision (source of truth for B6 receipts). */
    suspend fun revisionOf(id: String): Long = dao.getById(id)?.revision ?: 0L

    /** Roadmap B8 — current [ProcedureReviewState] for a procedure, defaulting to [ProcedureReviewState.REJECTED] when absent. */
    suspend fun reviewStateOf(id: String): ProcedureReviewState {
        val row = dao.getById(id) ?: return ProcedureReviewState.REJECTED
        return row.toReviewState()
    }

    /** Roadmap B8 — explicit review-state transition. ENABLED is the only executable state. */
    suspend fun setReviewState(id: String, state: ProcedureReviewState): Boolean {
        val row = dao.getById(id) ?: return false
        dao.update(
            row.copy(
                enabled = state.executable,
                updatedAtMs = System.currentTimeMillis(),
            )
        )
        return true
    }

    /** Roadmap B8 — list procedures in a given review state. */
    suspend fun listByReviewState(state: ProcedureReviewState): List<ZeroProcedure> =
        dao.listAll().filter { it.toReviewState() == state }.mapNotNull { row ->
            runCatching { json.decodeFromString(ZeroProcedure.serializer(), row.procedureJson) }.getOrNull()
        }

    suspend fun delete(id: String): Boolean = dao.deleteById(id) > 0

    suspend fun observeEnabled(ids: List<String>): List<ZeroProcedure> =
        dao.listAll().filter { row -> row.enabled && (ids.isEmpty() || ids.contains(row.id)) }
            .mapNotNull { row ->
                runCatching { json.decodeFromString(ZeroProcedure.serializer(), row.procedureJson) }.getOrNull()
            }

    suspend fun listBySource(source: ProcedureSource): List<ZeroProcedure> =
        dao.listBySource(source.wire).mapNotNull { row ->
            runCatching { json.decodeFromString(ZeroProcedure.serializer(), row.procedureJson) }.getOrNull()
        }
}

/**
 * Roadmap B8 — derive the explicit review state from the stored disposition.
 * A procedure is ENABLED only when the enabled flag is set; everything else maps to a
 * non-executable review state (mined/imported candidates are disabled by default, so a
 * disabled non-mined row is REJECTED and a disabled MINED row is CANDIDATE).
 */
private fun ZeroProcedureEntity.toReviewState(): ProcedureReviewState = when {
    enabled -> ProcedureReviewState.ENABLED
    source == ProcedureSource.MINED.wire -> ProcedureReviewState.CANDIDATE
    else -> ProcedureReviewState.REJECTED
}
