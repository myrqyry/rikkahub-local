package me.rerere.rikkahub.data.agentrun

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.rikkahub.data.db.AppDatabase
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

data class CreateAgentRun(
    val kind: AgentRunKind,
    val domainId: String,
    val parentRunId: String? = null,
    val status: AgentRunStatus = AgentRunStatus.running,
    val metadata: JsonObject? = null,
)

data class AgentRunOutcome(
    val status: AgentRunStatus,
    val terminalReason: String? = null,
)

sealed interface FinishRunResult {
    data object Finished : FinishRunResult
    data object AlreadyTerminal : FinishRunResult
    data object Missing : FinishRunResult
    data object InvalidOutcome : FinishRunResult
}

/**
 * Append-only trace repository. Each [AgentRun] is the summary/recovery row; this repository
 * writes the immutable [AgentRunEvent] stream (sequence-assigned per run) and keeps the run
 * row's summary fields in sync, all in single Room transactions. Operational events are
 * rejected after the run reaches a terminal state.
 */
class AgentRunTraceRepository(
    private val database: AppDatabase,
) {
    private val writeMutex = Mutex()
    private val runDao by lazy { database.agentRunDao() }
    private val eventDao by lazy { database.agentRunEventDao() }
    private val sanitizer by lazy { TracePayloadSanitizer() }

    suspend fun createRun(request: CreateAgentRun): AgentRun {
        val now = System.currentTimeMillis()
        val run = AgentRun(
            id = Uuid.random().toString(),
            kind = request.kind.wire,
            domainId = request.domainId,
            parentRunId = request.parentRunId,
            status = request.status.name,
            createdAtMs = now,
            updatedAtMs = now,
            startedAtMs = if (request.status == AgentRunStatus.running) now else null,
            finishedAtMs = null,
            lastError = null,
            metadataJson = request.metadata?.toString()?.take(METADATA_MAX_BYTES),
        )
        writeMutex.withLock {
            database.withTransaction {
                runDao.insert(run)
                eventDao.insert(
                    AgentRunEvent(
                        id = Uuid.random().toString(),
                        runId = run.id,
                        sequence = 0,
                        type = AgentRunEventType.RUN_CREATED.name,
                        createdAtMs = now,
                        severity = TraceSeverity.INFO.name,
                    )
                )
            }
        }
        return run
    }

    suspend fun append(runId: String, event: NewAgentRunEvent): AgentRunEvent {
        return writeMutex.withLock {
            database.withTransaction {
                val run = checkNotNull(runDao.getById(runId)) { "Agent run does not exist: $runId" }
                check(!AgentRunStatus.fromName(run.status).isTerminal) { "Agent run is already terminal: $runId" }
                val now = System.currentTimeMillis()
                val sanitized = sanitizer.sanitize(event.summary, event.payloadJson)
                val persisted = AgentRunEvent(
                    id = Uuid.random().toString(),
                    runId = runId,
                    sequence = eventDao.findNextSequence(runId),
                    type = event.type.name,
                    createdAtMs = now,
                    severity = event.severity.name,
                    summary = sanitized.summary,
                    toolName = event.toolName,
                    operationId = event.operationId,
                    effectCategory = event.effectCategory,
                    payloadJson = sanitized.payloadJson,
                )
                eventDao.insert(persisted)

                val summary = run.copy(
                    status = statusFor(event.type, run.status),
                    updatedAtMs = now,
                    startedAtMs = run.startedAtMs ?: if (event.type == AgentRunEventType.RUN_STARTED) now else null,
                    finishedAtMs = if (event.type.isTerminal) now else run.finishedAtMs,
                    lastError = if (event.type == AgentRunEventType.RUN_FAILED) sanitized.summary else run.lastError,
                )
                if (summary != run) runDao.update(summary)
                persisted
            }
        }
    }

    suspend fun finish(runId: String, outcome: AgentRunOutcome): FinishRunResult {
        return writeMutex.withLock {
            database.withTransaction {
                val run = runDao.getById(runId) ?: return@withTransaction FinishRunResult.Missing
                if (AgentRunStatus.fromName(run.status).isTerminal) return@withTransaction FinishRunResult.AlreadyTerminal
                val eventType = when (outcome.status) {
                    AgentRunStatus.succeeded -> AgentRunEventType.RUN_COMPLETED
                    AgentRunStatus.failed -> AgentRunEventType.RUN_FAILED
                    AgentRunStatus.cancelled -> AgentRunEventType.RUN_CANCELLED
                    else -> return@withTransaction FinishRunResult.InvalidOutcome
                }
                val now = System.currentTimeMillis()
                eventDao.insert(
                    AgentRunEvent(
                        id = Uuid.random().toString(),
                        runId = runId,
                        sequence = eventDao.findNextSequence(runId),
                        type = eventType.name,
                        createdAtMs = now,
                        severity = if (eventType == AgentRunEventType.RUN_FAILED) TraceSeverity.ERROR.name else TraceSeverity.INFO.name,
                        summary = outcome.terminalReason?.take(MAX_SUMMARY_CHARS),
                    )
                )
                runDao.update(
                    run.copy(
                        status = outcome.status.name,
                        updatedAtMs = now,
                        finishedAtMs = now,
                        lastError = outcome.terminalReason?.take(LAST_ERROR_MAX) ?: run.lastError,
                    )
                )
                FinishRunResult.Finished
            }
        }
    }

    fun observeRun(runId: String): Flow<AgentRun?> = runDao.observeById(runId)

    fun observeEvents(runId: String): Flow<List<AgentRunEvent>> = eventDao.findByRun(runId)

    /** Latest successful tool-execution events across all runs, oldest first (roadmap B7 persisted-history mining). */
    suspend fun successfulToolHistory(limit: Int = 500): List<AgentRunEvent> =
        eventDao.findSuccessfulToolEvents(limit)

    private fun statusFor(type: AgentRunEventType, current: String): String = when (type) {
        AgentRunEventType.RUN_STARTED -> AgentRunStatus.running.name
        AgentRunEventType.RUN_COMPLETED -> AgentRunStatus.succeeded.name
        AgentRunEventType.RUN_FAILED -> AgentRunStatus.failed.name
        AgentRunEventType.RUN_CANCELLED -> AgentRunStatus.cancelled.name
        else -> current
    }

    companion object {
        const val MAX_SUMMARY_CHARS = 2_000
        private const val LAST_ERROR_MAX = 2_000
        private const val METADATA_MAX_BYTES = 4 * 1024
    }
}
