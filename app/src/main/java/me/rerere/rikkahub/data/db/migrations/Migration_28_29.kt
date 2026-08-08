package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `agent_run_events` (
                `id` TEXT NOT NULL,
                `run_id` TEXT NOT NULL,
                `sequence` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `created_at_ms` INTEGER NOT NULL,
                `severity` TEXT NOT NULL,
                `summary` TEXT,
                `tool_name` TEXT,
                `operation_id` TEXT,
                `effect_category` TEXT,
                `payload_json` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`run_id`) REFERENCES `agent_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_agent_run_events_run_id_sequence` " +
                "ON `agent_run_events` (`run_id`, `sequence`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_agent_run_events_run_id_created_at_ms` " +
                "ON `agent_run_events` (`run_id`, `created_at_ms`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_run_events_type` ON `agent_run_events` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_run_events_tool_name` ON `agent_run_events` (`tool_name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_run_events_operation_id` ON `agent_run_events` (`operation_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_run_events_effect_category` ON `agent_run_events` (`effect_category`)")
    }
}
