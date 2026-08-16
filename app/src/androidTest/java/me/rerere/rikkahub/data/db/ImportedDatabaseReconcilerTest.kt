package me.rerere.rikkahub.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.data.db.migrations.Migration_27_28
import me.rerere.rikkahub.data.db.migrations.Migration_28_29
import me.rerere.rikkahub.data.db.migrations.Migration_29_30
import me.rerere.rikkahub.data.db.migrations.Migration_30_31
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for issues #10 / #11: restoring an *upstream* RikkaHub 2.4.1 backup into the
 * fork crashed on the first launch after the import with
 * "duplicate column name: custom_system_prompt".
 *
 * Upstream 2.4.1 stamps its database at user_version 24, but its ConversationEntity already
 * carries custom_system_prompt / workspace_cwd / folder_id (upstream added them at its own
 * earlier versions). The fork's schema-equivalent version is 27, so an un-reconciled restore
 * makes Room replay the fork's 24 -> 25 auto-migration, which re-ADDs custom_system_prompt and
 * crashes. The fork's shared tables at v27 are byte-for-byte identical to upstream's at v24, so
 * [ImportedDatabaseReconciler] stamps such a file straight to v27 and skips the replay.
 *
 * The test builds a faithful upstream-2.4.1 file (fork v27 shared schema, upstream's version +
 * identity, fork-only tables removed) and asserts:
 *  - without reconcile, opening it through Room reproduces the reported duplicate-column crash;
 *  - after reconcile, Room opens it cleanly, the seeded conversation survives, and every
 *    fork-only table exists and starts empty.
 */
@RunWith(AndroidJUnit4::class)
class ImportedDatabaseReconcilerTest {

    private val TEST_DB = "reconciler-upstream-241-test"

    // Upstream RikkaHub 2.4.1's actual stamp: user_version 24 plus its own (foreign) identity.
    private val UPSTREAM_VERSION = 24
    private val UPSTREAM_IDENTITY = "0ea1aaebfa031c7995c45a1e35822e1a"

    private val FORK_ONLY_TABLES = listOf(
        "scheduled_jobs", "scheduled_job_runs", "ssh_hosts", "telegram_chats",
        "workflows", "workflow_runs", "agent_runs",
    )

    // Tables created by the registered migration chain (schema 30 / 31), not present in an
    // upstream 2.4.1 (fork v27) backup.
    private val CHAIN_CREATED_TABLES = listOf(
        "agent_run_events", "zero_procedures",
    )

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun withoutReconcile_upstream241Backup_reproducesDuplicateColumnCrash() {
        createUpstream241Backup(conversationId = "c1")

        val room = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .allowMainThreadQueries()
            .addMigrations(Migration_27_28, Migration_28_29, Migration_29_30, Migration_30_31)
            .build()
        try {
            // Forcing the db open replays the 24 -> 25 auto-migration; this is where the
            // reported crash fired.
            room.openHelper.writableDatabase
                .query("SELECT COUNT(*) FROM ConversationEntity").use { it.moveToFirst() }
            fail("expected Room to crash replaying the 24 -> 25 migration on an upstream 2.4.1 file")
        } catch (expected: Throwable) {
            val chain = generateSequence<Throwable>(expected) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" | ")
            assertTrue(
                "expected a duplicate-column failure on custom_system_prompt, got: $chain",
                chain.contains("custom_system_prompt") || chain.contains("duplicate column"),
            )
        } finally {
            room.close()
        }
    }

    @Test
    fun afterReconcile_upstream241Backup_opensAndKeepsData() {
        createUpstream241Backup(conversationId = "c1")

        ImportedDatabaseReconciler.reconcileDatabaseFile(context.getDatabasePath(TEST_DB))

        // Reconcile stamps the file at fork v27; opening through the real builder at v31 replays
        // the full registered chain 27 -> 31. v27 is an intermediate reconciliation target, not
        // the final schema.
        val room = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .allowMainThreadQueries()
            .addMigrations(Migration_27_28, Migration_28_29, Migration_29_30, Migration_30_31)
            .build()
        try {
            val db = room.openHelper.writableDatabase // opens + validates + migrates to 31

            db.query("SELECT title, revision FROM ConversationEntity WHERE id = 'c1'").use { c ->
                assertTrue("seeded conversation row should survive the restore", c.moveToFirst())
                assertEquals("hello", c.getString(0))
                assertEquals("migrated conversation revision should default to 0", 0L, c.getLong(1))
            }

            for (table in FORK_ONLY_TABLES) {
                db.query("SELECT COUNT(*) FROM `$table`").use { c ->
                    assertTrue("fork-only table $table should exist after reconcile", c.moveToFirst())
                    assertEquals("fork-only table $table should start empty", 0, c.getInt(0))
                }
            }

            for (table in CHAIN_CREATED_TABLES) {
                db.query("SELECT COUNT(*) FROM `$table`").use { c ->
                    assertTrue("schema-30/31 table $table should exist after full chain", c.moveToFirst())
                    assertEquals("schema-30/31 table $table should start empty", 0, c.getInt(0))
                }
            }

            // schema-30/31 tables are present: agent_run_events FK targets agent_runs.
            db.query(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'agent_run_events'",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertTrue("agent_run_events must reference agent_runs", c.getString(0).contains("REFERENCES"))
            }
        } finally {
            room.close()
        }
    }

    /**
     * Writes a file that looks exactly like an upstream RikkaHub 2.4.1 backup: start from a
     * genuine fork v27 database (Room creates every table and stamps the v27 identity), seed a
     * conversation, then downgrade the file on disk by dropping the fork-only tables and
     * stamping upstream's user_version + identity.
     */
    private fun createUpstream241Backup(conversationId: String) {
        val room = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .allowMainThreadQueries()
            .build()
        try {
            room.openHelper.writableDatabase.execSQL(
                "INSERT INTO ConversationEntity (id, title, nodes, create_at, update_at) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>(conversationId, "hello", "[]", 1_000L, 1_000L),
            )
        } finally {
            room.close() // checkpoints WAL so the raw handle below sees a settled file
        }

        val dbFile = context.getDatabasePath(TEST_DB)
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            FORK_ONLY_TABLES.forEach { raw.execSQL("DROP TABLE IF EXISTS `$it`") }
            raw.execSQL(
                "UPDATE room_master_table SET identity_hash = ? WHERE id = 42",
                arrayOf<Any?>(UPSTREAM_IDENTITY),
            )
            raw.version = UPSTREAM_VERSION // PRAGMA user_version = 24
        }
    }
}
