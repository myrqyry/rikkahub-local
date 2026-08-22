package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_31_32 : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `agent_evidence` (" +
                "`id` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`payload` TEXT NOT NULL, " +
                "`origin` TEXT NOT NULL, " +
                "`session_id` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`)" +
                ")",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_evidence_type` ON `agent_evidence` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_evidence_origin` ON `agent_evidence` (`origin`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_evidence_session_id` ON `agent_evidence` (`session_id`)")
    }
}
