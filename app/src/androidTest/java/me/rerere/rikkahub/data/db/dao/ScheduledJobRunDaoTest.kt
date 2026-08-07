package me.rerere.rikkahub.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ScheduledJobRunEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduledJobRunDaoTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AppDatabase
    private lateinit var dao: ScheduledJobRunDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.scheduledJobRunDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun trim_keepsIndependentSuccessAndFailureWindows() = runBlocking {
        // Successes are deliberately older than every failure. The previous mixed newest-N
        // query retained only failures here, causing countSuccessful() to fall from 3 to 0.
        insert(jobId = "job", id = "success-1", startedAtMs = 1L, outcome = "success")
        insert(jobId = "job", id = "success-2", startedAtMs = 2L, outcome = "success")
        insert(jobId = "job", id = "success-3", startedAtMs = 3L, outcome = "success")
        insert(jobId = "job", id = "failed-10", startedAtMs = 10L, outcome = "failed")
        insert(jobId = "job", id = "failed-11", startedAtMs = 11L, outcome = "failed")
        insert(jobId = "job", id = "timeout-12", startedAtMs = 12L, outcome = "timed_out")
        insert(jobId = "job", id = "skip-13", startedAtMs = 13L, outcome = "concurrent_skip")
        insert(jobId = "job", id = "failed-14", startedAtMs = 14L, outcome = "failed")

        // Ensure the job filter remains scoped while trimming.
        insert(jobId = "other", id = "other-success", startedAtMs = 100L, outcome = "success")

        dao.trim(jobId = "job", keep = 2)

        val retained = dao.getRecent(jobId = "job", limit = 100)
        assertEquals(
            setOf("success-2", "success-3", "skip-13", "failed-14"),
            retained.map { it.id }.toSet(),
        )
        assertEquals(4, retained.size)
        assertEquals(2, dao.countSuccessful("job"))
        assertEquals(listOf("other-success"), dao.getRecent("other", 100).map { it.id })
    }

    @Test
    fun trim_withZeroKeep_deletesAllHistoryForTheJob() = runBlocking {
        insert(jobId = "job", id = "success", startedAtMs = 1L, outcome = "success")
        insert(jobId = "job", id = "failed", startedAtMs = 2L, outcome = "failed")
        insert(jobId = "other", id = "other", startedAtMs = 3L, outcome = "success")

        dao.trim(jobId = "job", keep = 0)

        assertEquals(emptyList<ScheduledJobRunEntity>(), dao.getRecent("job", 100))
        assertEquals(listOf("other"), dao.getRecent("other", 100).map { it.id })
    }

    private suspend fun insert(
        jobId: String,
        id: String,
        startedAtMs: Long,
        outcome: String,
    ) {
        dao.insert(
            ScheduledJobRunEntity(
                id = id,
                jobId = jobId,
                mode = "llm",
                scheduledAtMs = startedAtMs,
                startedAtMs = startedAtMs,
                finishedAtMs = startedAtMs + 1,
                outcome = outcome,
            ),
        )
    }
}
