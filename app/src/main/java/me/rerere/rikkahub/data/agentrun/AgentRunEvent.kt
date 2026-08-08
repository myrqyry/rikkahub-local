package me.rerere.rikkahub.data.agentrun

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Immutable, ordered evidence emitted during an [AgentRun]. */
@Entity(
    tableName = "agent_run_events",
    foreignKeys = [
        ForeignKey(
            entity = AgentRun::class,
            parentColumns = ["id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["run_id", "sequence"], unique = true),
        Index(value = ["run_id", "created_at_ms"]),
        Index(value = ["type"]),
        Index(value = ["tool_name"]),
        Index(value = ["operation_id"]),
        Index(value = ["effect_category"]),
    ],
)
data class AgentRunEvent(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "run_id")
    val runId: String,

    @ColumnInfo(name = "sequence")
    val sequence: Long,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,

    @ColumnInfo(name = "severity")
    val severity: String,

    @ColumnInfo(name = "summary")
    val summary: String? = null,

    @ColumnInfo(name = "tool_name")
    val toolName: String? = null,

    @ColumnInfo(name = "operation_id")
    val operationId: String? = null,

    @ColumnInfo(name = "effect_category")
    val effectCategory: String? = null,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String? = null,
)

/** Event input before the repository assigns identity, run, sequence, and time. */
data class NewAgentRunEvent(
    val type: AgentRunEventType,
    val severity: TraceSeverity = TraceSeverity.INFO,
    val summary: String? = null,
    val toolName: String? = null,
    val operationId: String? = null,
    val effectCategory: String? = null,
    val payloadJson: String? = null,
)

@Suppress("EnumEntryName")
enum class AgentRunEventType {
    RUN_CREATED,
    RUN_STARTED,
    STAGE_CHANGED,
    MODEL_RESOLVED,
    MODEL_CALL_STARTED,
    MODEL_CALL_COMPLETED,
    MODEL_CALL_FAILED,
    TOOL_PROPOSED,
    TOOL_REVIEWED,
    TOOL_APPROVED,
    TOOL_REJECTED,
    TOOL_STARTED,
    TOOL_PROGRESS,
    TOOL_COMPLETED,
    TOOL_FAILED,
    TOOL_CANCELLED,
    PRIVACY_CHECKED,
    REVISION_CHECKED,
    RESULT_DISCARDED,
    ARTIFACT_CREATED,
    WARNING_RECORDED,
    RUN_COMPLETED,
    RUN_FAILED,
    RUN_CANCELLED,
    ;

    val isTerminal: Boolean
        get() = this == RUN_COMPLETED || this == RUN_FAILED || this == RUN_CANCELLED
}

@Suppress("EnumEntryName")
enum class TraceSeverity {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}
