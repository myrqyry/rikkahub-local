package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_28_29 : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vector_store ADD COLUMN embeddingSpaceId TEXT NOT NULL DEFAULT 'legacy'")
        db.execSQL("ALTER TABLE vector_store ADD COLUMN embeddingDimension INTEGER NOT NULL DEFAULT 0")
    }
}
