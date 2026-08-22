package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_32_33 : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `continuation_checkpoints` (" +
                "`id` TEXT NOT NULL, " +
                "`run_id` TEXT NOT NULL, " +
                "`sequence` INTEGER NOT NULL, " +
                "`created_at_ms` INTEGER NOT NULL, " +
                "`verified_at_ms` INTEGER NOT NULL, " +
                "`snapshot_json` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`)" +
                ")",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_continuation_checkpoints_run_id` ON `continuation_checkpoints` (`run_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_continuation_checkpoints_run_id_sequence` ON `continuation_checkpoints` (`run_id`, `sequence`)")
    }
}
