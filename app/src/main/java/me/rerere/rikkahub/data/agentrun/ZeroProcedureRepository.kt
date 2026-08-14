package me.rerere.rikkahub.data.agentrun

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.locallm.litert.zero.ProcedureCache
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
