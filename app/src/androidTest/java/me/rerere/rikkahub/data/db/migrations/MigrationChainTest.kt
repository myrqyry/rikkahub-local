package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0 stabilization: prove every supported recent schema migrates cleanly to the current
 * version (32) through the exact production chain. Each start version seeds representative
 * rows before migration and validates rows, defaults, foreign keys, and indexes afterwards.
 */
@RunWith(AndroidJUnit4::class)
class MigrationChainTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val migrations = listOf(
        Migration_27_28,
        Migration_28_29,
        Migration_29_30,
        Migration_30_31,
        Migration_31_32,
    )

    private fun chainFrom(startVersion: Int): Array<Migration> {
        val idx = when (startVersion) {
            27 -> 0
            28 -> 1
            29 -> 2
            30 -> 3
            31 -> 4
            else -> error("unsupported start version $startVersion")
        }
        return migrations.subList(idx, migrations.size).toTypedArray()
    }

    @Test
    fun migrate27To32_preservesSeededRowsAndSchema() = migrateChainFrom(27)

    @Test
    fun migrate28To32_preservesSeededRowsAndSchema() = migrateChainFrom(28)

    @Test
    fun migrate29To32_preservesSeededRowsAndSchema() = migrateChainFrom(29)

    @Test
    fun migrate30To32_preservesSeededRowsAndSchema() = migrateChainFrom(30)

    @Test
    fun migrate31To32_preservesSeededRowsAndSchema() = migrateChainFrom(31)

    private fun migrateChainFrom(startVersion: Int) {
        val testDb = "migration-chain-$startVersion-to-32"

        helper.createDatabase(testDb, startVersion).apply {
            execSQL(
                "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at, " +
                    "suggestions, is_pinned, custom_system_prompt, mode_injection_ids, lorebook_ids, " +
                    "workspace_cwd, folder_id) VALUES ('c1','a','hello','[]',1,1,'[]',0,'','[]','[]','','')",
            )
            execSQL(
                "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) " +
                    "VALUES ('m1','c1',0,'[]',0)",
            )
            execSQL(
                "INSERT INTO agent_runs (id, kind, domain_id, status, created_at_ms, updated_at_ms) " +
                    "VALUES ('r1','procedure','d1','completed',1,1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 32, true, *chainFrom(startVersion))

        // Seeded conversation survives and revision defaults to 0 after the 29 -> 30 step.
        db.query("SELECT revision FROM ConversationEntity WHERE id = 'c1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("conversation revision must default to 0", 0L, c.getLong(0))
        }

        // Seeded message node survives.
        db.query("SELECT COUNT(*) FROM message_node WHERE id = 'm1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("seeded message node must survive", 1, c.getInt(0))
        }

        // agent_run_events (schema 30) exists with exactly one unique (run_id, sequence) index.
        db.query(
            "SELECT name, sql FROM sqlite_master WHERE type = 'index' AND tbl_name = 'agent_run_events'",
        ).use { c ->
            val seqIndexes = mutableListOf<Pair<String, Boolean>>()
            while (c.moveToNext()) {
                val sql = c.getString(1) ?: ""
                if (sql.contains("run_id") && sql.contains("sequence")) {
                    seqIndexes.add(c.getString(0) to sql.contains("UNIQUE"))
                }
            }
            assertEquals("exactly one (run_id, sequence) index", 1, seqIndexes.size)
            assertTrue("the index must be UNIQUE", seqIndexes.single().second)
        }

        // zero_procedures (schema 31) exists.
        db.query("SELECT COUNT(*) FROM zero_procedures").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("zero_procedures must exist and start empty", 0, c.getInt(0))
        }

        // agent_evidence (schema 32) exists independently of agent run lifecycle.
        db.query("SELECT COUNT(*) FROM agent_evidence").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("agent_evidence must exist and start empty", 0, c.getInt(0))
        }

        db.close()
    }
}
