package me.rerere.rikkahub.data.agentrun

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import me.rerere.locallm.litert.zero.ZeroProcedure
import me.rerere.locallm.litert.zero.ZeroStep
import me.rerere.rikkahub.data.db.entity.ZeroProcedureDao
import me.rerere.rikkahub.data.db.entity.ZeroProcedureEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val testJson = Json { ignoreUnknownKeys = true }

/** In-memory [ZeroProcedureDao] for JVM unit tests — no Room, no instrumentation. */
private class FakeZeroProcedureDao : ZeroProcedureDao {
    private val rows = linkedMapOf<String, ZeroProcedureEntity>()
    private val flow = MutableStateFlow<List<ZeroProcedureEntity>>(emptyList())
    private fun publish() { flow.value = rows.values.toList() }

    override fun observeAll(): Flow<List<ZeroProcedureEntity>> = flow
    override suspend fun listAll(): List<ZeroProcedureEntity> = rows.values.toList()
    override suspend fun getById(id: String): ZeroProcedureEntity? = rows[id]
    override fun observeById(id: String): Flow<ZeroProcedureEntity?> = flow.map { it.firstOrNull { r -> r.id == id } }
    override suspend fun listBySource(source: String): List<ZeroProcedureEntity> = rows.values.filter { it.source == source }
    override suspend fun upsert(entity: ZeroProcedureEntity) { rows[entity.id] = entity; publish() }
    override suspend fun update(entity: ZeroProcedureEntity) { rows[entity.id] = entity; publish() }
    override suspend fun deleteById(id: String): Int { val removed = rows.remove(id); if (removed != null) publish(); return if (removed != null) 1 else 0 }
}

private fun proc(id: String, description: String? = null) = ZeroProcedure(
    id = id,
    description = description,
    steps = listOf(ZeroStep(stepId = "s1", tool = "echo", args = buildJsonObject { })),
)

class ZeroProcedureRepositoryTest {

    private val dao = FakeZeroProcedureDao()
    private val repository = ZeroProcedureRepository(dao, testJson)

    @Test
    fun putThenGetRoundTrips() = runBlocking {
        repository.put(proc("p1", "desc"))
        val got = repository.get("p1")
        assertNotNull(got)
        assertEquals("p1", got!!.id)
        assertEquals("desc", got.description)
    }

    @Test
    fun putDefaultsSourceToUserAndEnabled() = runBlocking {
        repository.put(proc("p2"))
        val row = dao.getById("p2")
        assertNotNull(row)
        assertEquals("USER", row!!.source)
        assertTrue(row.enabled)
        assertEquals("pending", row.validationStatus)
        assertEquals(0, row.supportCount)
    }

    @Test
    fun putIncrementsRevisionAndPreservesCreatedAt() = runBlocking {
        repository.put(proc("p3"))
        val firstCreated = dao.getById("p3")!!.createdAtMs
        repository.put(proc("p3"))
        val row = dao.getById("p3")!!
        assertEquals(2L, row.revision)
        assertEquals(firstCreated, row.createdAtMs)
        assertTrue(row.updatedAtMs >= firstCreated)
    }

    @Test
    fun allReturnsEveryDecodable() = runBlocking {
        repository.put(proc("a"))
        repository.put(proc("b"))
        assertEquals(setOf("a", "b"), repository.all().map { it.id }.toSet())
    }

    @Test
    fun getEnabledReturnsNullWhenDisabled() = runBlocking {
        repository.put(proc("p4"))
        assertTrue(repository.setEnabled("p4", false))
        assertNull(repository.getEnabled("p4"))
        assertTrue(repository.setEnabled("p4", true))
        assertNotNull(repository.getEnabled("p4"))
    }

    @Test
    fun setEnabledReturnsFalseForUnknownId() = runBlocking {
        assertFalse(repository.setEnabled("missing", true))
    }

    @Test
    fun deleteRemovesRow() = runBlocking {
        repository.put(proc("p5"))
        assertTrue(repository.delete("p5"))
        assertNull(repository.get("p5"))
    }

    @Test
    fun listBySourceFilters() = runBlocking {
        repository.put(proc("user-1"))
        val mined = proc("mined-1")
        repository.put(mined)
        dao.update(dao.getById("mined-1")!!.copy(source = "MINED"))
        assertEquals(listOf("mined-1"), repository.listBySource(ProcedureSource.MINED).map { it.id })
    }
}
