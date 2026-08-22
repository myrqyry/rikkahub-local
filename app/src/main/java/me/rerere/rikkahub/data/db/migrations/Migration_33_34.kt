package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_33_34 : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `agent_evidence_new` (" +
                "`sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`id` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`payload` TEXT NOT NULL, " +
                "`origin` TEXT NOT NULL, " +
                "`session_id` TEXT NOT NULL" +
                ")",
        )
        db.execSQL(
            "INSERT INTO `agent_evidence_new` (`sequence`, `id`, `type`, `payload`, `origin`, `session_id`) " +
                "SELECT rowid, id, type, payload, origin, session_id FROM `agent_evidence` ORDER BY rowid ASC",
        )
        db.execSQL("DROP TABLE `agent_evidence`")
        db.execSQL("ALTER TABLE `agent_evidence_new` RENAME TO `agent_evidence`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_agent_evidence_id` ON `agent_evidence` (`id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_evidence_type` ON `agent_evidence` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_evidence_origin` ON `agent_evidence` (`origin`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_evidence_session_id` ON `agent_evidence` (`session_id`)")
    }
}
