package me.rerere.rikkahub.data.agentrun

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.agentruntime.ContinuationCheckpointDraft
import me.rerere.agentruntime.ContinuationSnapshot
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomContinuationStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "room-continuation-store-${UUID.randomUUID()}"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun append_closeAndReopen_returnsLatestCheckpoint() = runBlocking {
        val first = openStore()
        first.second.append(draft("c1", "run-a"))
        val latest = first.second.append(draft("c2", "run-a", goal = "verify"))
        first.first.close()

        val reopened = openStore()
        assertEquals(latest, reopened.second.latest("run-a"))
        assertEquals(listOf("c1", "c2"), reopened.second.list("run-a").map { it.id })
        reopened.first.close()
    }

    private fun openStore(): Pair<AppDatabase, RoomContinuationStore> {
        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        return database to RoomContinuationStore(database.continuationCheckpointDao())
    }

    private fun draft(id: String, runId: String, goal: String = "continue") =
        ContinuationCheckpointDraft(
            id = id,
            runId = runId,
            verifiedAtMs = 10L,
            snapshot = ContinuationSnapshot(
                goal = goal,
                pendingWork = "pending",
                lastVerifiedAction = "verified",
                verificationState = "passed",
            ),
        )
}
