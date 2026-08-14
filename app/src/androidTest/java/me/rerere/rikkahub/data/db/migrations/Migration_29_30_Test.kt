package me.rerere.rikkahub.data.db.migrations

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

@RunWith(AndroidJUnit4::class)
class Migration_29_30_Test {
    private val testDb = "migration-29-30-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate29To30_addsConversationRevisionWithZeroDefault() {
        helper.createDatabase(testDb, 29).apply {
            execSQL("INSERT INTO conversationentity (id, assistant_id, title, nodes, create_at, update_at, suggestions, is_pinned, custom_system_prompt, mode_injection_ids, lorebook_ids, workspace_cwd, folder_id) VALUES ('c', 'a', '', '[]', 1, 1, '[]', 0, '', '[]', '[]', '', '')")
            close()
        }
        val db = helper.runMigrationsAndValidate(testDb, 30, true, Migration_29_30)
        val cursor = db.query("SELECT revision FROM conversationentity WHERE id = 'c'")
        assertTrue(cursor.moveToFirst())
        assertEquals(0L, cursor.getLong(0))
        cursor.close()
        db.close()
    }
}
