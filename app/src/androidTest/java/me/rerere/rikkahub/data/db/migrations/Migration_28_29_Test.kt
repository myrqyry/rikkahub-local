package me.rerere.rikkahub.data.db.migrations

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_28_29_Test {
    private val testDb = "migration-28-29-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate28To29_createsTraceTableAndIndexes() {
        helper.createDatabase(testDb, 28).close()
        val db = helper.runMigrationsAndValidate(testDb, 29, true, Migration_28_29)

        val columns = db.query("SELECT * FROM agent_run_events LIMIT 0").use { it.columnNames.toList() }
        for (column in listOf(
            "id", "run_id", "sequence", "type", "created_at_ms", "severity",
            "summary", "tool_name", "operation_id", "effect_category", "payload_json",
        )) {
            assertTrue("agent_run_events should have '$column'", columns.contains(column))
        }

        for (index in listOf(
            "index_agent_run_events_run_id_sequence",
            "index_agent_run_events_run_id_created_at_ms",
            "index_agent_run_events_type",
            "index_agent_run_events_tool_name",
            "index_agent_run_events_operation_id",
            "index_agent_run_events_effect_category",
        )) {
            db.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
                arrayOf(index),
            ).use { cursor -> assertTrue("index $index should exist", cursor.moveToFirst()) }
        }
        db.close()
    }

    @Test
    fun migrate28To29_cascadesEventsWhenRunIsDeleted() {
        helper.createDatabase(testDb, 28).close()
        val db = helper.runMigrationsAndValidate(testDb, 29, true, Migration_28_29)
        db.insert("agent_runs", 0, ContentValues().apply {
            put("id", "run-1")
            put("kind", "cron")
            put("domain_id", "job-1")
            put("status", "succeeded")
            put("created_at_ms", 1L)
            put("updated_at_ms", 1L)
        })
        db.insert("agent_run_events", 0, ContentValues().apply {
            put("id", "event-1")
            put("run_id", "run-1")
            put("sequence", 0L)
            put("type", "RUN_COMPLETED")
            put("created_at_ms", 1L)
            put("severity", "INFO")
        })

        db.delete("agent_runs", "id = ?", arrayOf("run-1"))
        db.query("SELECT id FROM agent_run_events WHERE run_id = ?", arrayOf("run-1")).use { cursor ->
            assertTrue("events should cascade when their run is deleted", !cursor.moveToFirst())
        }
        db.close()
    }
}
