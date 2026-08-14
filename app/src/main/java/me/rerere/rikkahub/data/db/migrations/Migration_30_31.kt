package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_30_31 : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `zero_procedures` (" +
                "`id` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`enabled` INTEGER NOT NULL DEFAULT 1, " +
                "`revision` INTEGER NOT NULL DEFAULT 0, " +
                "`validationStatus` TEXT NOT NULL DEFAULT 'pending', " +
                "`supportCount` INTEGER NOT NULL DEFAULT 0, " +
                "`procedureJson` TEXT NOT NULL, " +
                "`createdAtMs` INTEGER NOT NULL DEFAULT 0, " +
                "`updatedAtMs` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`id`) " +
                ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_zero_procedures_enabled` ON `zero_procedures` (`enabled`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_zero_procedures_source` ON `zero_procedures` (`source`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_zero_procedures_supportCount` ON `zero_procedures` (`supportCount`)")
    }
}
