package me.rerere.rikkahub.data.agentrun

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.agentruntime.EvidenceQuery
import me.rerere.agentruntime.EvidenceRecord
import me.rerere.agentruntime.EvidenceWriteResult
import me.rerere.agentruntime.ProvenanceAnchor
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomEvidenceStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "room-evidence-store-test"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun put_reopenAndQuery_preservesImmutableEvidence() = runBlocking {
        val first = openStore()
        val record = evidence(id = "e1", type = "trajectory", origin = "agent", sessionId = "s1")
        val second = evidence(id = "e2", type = "evaluation", origin = "agent", sessionId = "s1")

        assertEquals(EvidenceWriteResult.Stored, first.second.put(record))
        assertEquals(EvidenceWriteResult.Stored, first.second.put(second))
        first.first.close()

        val reopened = openStore()
        assertEquals(record, reopened.second.get(record.id))
        assertEquals(
            EvidenceWriteResult.Duplicate(record),
            reopened.second.put(record.copy(payload = "replacement")),
        )
        assertEquals(listOf("e1", "e2"), reopened.second.query().map { it.id })
        assertEquals(listOf(record), reopened.second.query(EvidenceQuery(type = "trajectory", origin = "agent")))
        assertNull(reopened.second.get("missing"))
        reopened.first.close()
    }

    private fun openStore(): Pair<AppDatabase, RoomEvidenceStore> {
        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        return database to RoomEvidenceStore(database.evidenceDao())
    }

    private fun evidence(id: String, type: String, origin: String, sessionId: String) = EvidenceRecord(
        id = id,
        type = type,
        payload = "payload-$id",
        provenance = ProvenanceAnchor(origin = origin, sessionId = sessionId),
    )
}
