package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migration_27_28"

val Migration_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 27 to 28 (creating vector_store table)")
        db.beginTransaction()
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `vector_store` (
                    `id` TEXT NOT NULL,
                    `embedding` BLOB NOT NULL,
                    `metadata` TEXT NOT NULL DEFAULT '{}',
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 27 to 28 success")
        } finally {
            db.endTransaction()
        }
    }
}