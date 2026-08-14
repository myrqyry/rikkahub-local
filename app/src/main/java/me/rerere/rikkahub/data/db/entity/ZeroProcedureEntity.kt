package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted projection of a [me.rerere.locallm.litert.zero.ZeroProcedure].
 *
 * The [procedureJson] is the source of truth (parsed back into a `ZeroProcedure` on read);
 * the projected columns support listing, gating and mining without parsing every row.
 * Mirrors the WorkflowEntity JSON-as-source-of-truth pattern.
 */
@Entity(
    tableName = "zero_procedures",
    indices = [
        Index(value = ["enabled"]),
        Index(value = ["source"]),
        Index(value = ["supportCount"]),
    ],
)
data class ZeroProcedureEntity(
    @PrimaryKey val id: String,
    /** Authoring source: USER, SKILL, MINED or GENERATED. */
    val source: String,
    /** Whether the procedure may be selected/executed. Mined procedures start disabled. */
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    /** Monotonic revision of the stored procedure. */
    @ColumnInfo(defaultValue = "0") val revision: Long = 0,
    /** validation status: pending / valid / invalid. */
    @ColumnInfo(defaultValue = "pending") val validationStatus: String = "pending",
    /** Support count from the miner, if any. */
    @ColumnInfo(defaultValue = "0") val supportCount: Int = 0,
    /** Canonical serialized [me.rerere.locallm.litert.zero.ZeroProcedure]. */
    val procedureJson: String,
    @ColumnInfo(defaultValue = "0") val createdAtMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val updatedAtMs: Long = 0,
)
