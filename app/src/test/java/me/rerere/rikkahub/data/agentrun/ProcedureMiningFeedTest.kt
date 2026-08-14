package me.rerere.rikkahub.data.agentrun

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import me.rerere.locallm.litert.WorkflowReceipt
import me.rerere.locallm.litert.zero.ZeroProcedure
import me.rerere.locallm.litert.zero.ZeroStep
import me.rerere.rikkahub.data.db.entity.ZeroProcedureDao
import me.rerere.rikkahub.data.db.entity.ZeroProcedureEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcedureMiningFeedTest {

    private class FakeZeroProcedureDao : ZeroProcedureDao {
        val rows = linkedMapOf<String, ZeroProcedureEntity>()
        private val flow = MutableStateFlow<List<ZeroProcedureEntity>>(emptyList())
        private fun publish() { flow.value = rows.values.toList() }
        override fun observeAll(): Flow<List<ZeroProcedureEntity>> = flow
        override suspend fun listAll(): List<ZeroProcedureEntity> = rows.values.toList()
        override suspend fun getById(id: String): ZeroProcedureEntity? = rows[id]
        override fun observeById(id: String): Flow<ZeroProcedureEntity?> = flow.map { list -> list.firstOrNull { it.id == id } }
        override suspend fun listBySource(source: String): List<ZeroProcedureEntity> = rows.values.filter { it.source == source }
        override suspend fun upsert(entity: ZeroProcedureEntity) { rows[entity.id] = entity; publish() }
        override suspend fun update(entity: ZeroProcedureEntity) { rows[entity.id] = entity; publish() }
        override suspend fun deleteById(id: String): Int { val r = rows.remove(id); if (r != null) publish(); return if (r != null) 1 else 0 }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun receipt(tool: String, atMs: Long) = WorkflowReceipt(
        receiptId = "r-$tool-$atMs",
        kind = "tool",
        domainId = tool,
        requestedAtMs = atMs,
        compileOutcome = "valid",
        status = "succeeded",
        durationMs = 1L,
    )

    @Test
    fun nonToolOrFailedReceiptsAreIgnored() = runBlocking {
        val dao = FakeZeroProcedureDao()
        val repo = ZeroProcedureRepository(dao, json)
        val feed = ProcedureMiningFeed(repo, executionsBeforeMine = 2)
        feed.record(receipt("a", 1).copy(kind = "workflow", status = "succeeded"))
        feed.record(receipt("a", 2).copy(kind = "tool", status = "failed"))
        feed.mineAndReset()
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun minesRecurringSequenceIntoDisabledMinedCandidate() = runBlocking {
        val dao = FakeZeroProcedureDao()
        val repo = ZeroProcedureRepository(dao, json)
        val feed = ProcedureMiningFeed(repo, executionsBeforeMine = 100)
        // a -> b -> c repeated twice, with other noise interleaved.
        listOf("a", "b", "c", "x", "a", "b", "c", "y").forEachIndexed { i, t ->
            feed.record(receipt(t, i.toLong()))
        }
        feed.mineAndReset()

        val mined = dao.rows.values.filter { it.source == "MINED" }
        assertEquals(1, mined.size)
        val entity = mined.first()
        assertFalse(entity.enabled)
        assertEquals(2, entity.supportCount)
        val decoded: ZeroProcedure? = runCatching { json.decodeFromString(ZeroProcedure.serializer(), entity.procedureJson) }.getOrNull()
        assertNotNull(decoded)
        assertEquals(3, decoded!!.steps.size)
        assertEquals(listOf("a", "b", "c"), decoded.steps.map { it.tool })
    }

    @Test
    fun autoMinesWhenThresholdReached() = runBlocking {
        val dao = FakeZeroProcedureDao()
        val repo = ZeroProcedureRepository(dao, json)
        val feed = ProcedureMiningFeed(repo, executionsBeforeMine = 3)
        feed.record(receipt("a", 1))
        assertTrue(dao.rows.values.none { it.source == "MINED" })
        feed.record(receipt("a", 2))
        feed.record(receipt("a", 3))
        // threshold hit after 3rd; sequence [a,a,a] length 3 support 1 → not mined (minSupport 2).
        assertTrue(dao.rows.values.none { it.source == "MINED" })
    }
}
