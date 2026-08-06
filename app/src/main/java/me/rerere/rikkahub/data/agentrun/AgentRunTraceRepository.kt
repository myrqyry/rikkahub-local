package me.rerere.rikkahub.data.agentrun

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import me.rerere.rikkahub.data.db.AppDatabase
import kotlin.uuid.Uuid

class AgentRunTraceRepository(
    private val database: AppDatabase,
) {
    private val writeMutex = Mutex()
    private val runDao get() = database.agentRunDao()
    private val eventDao get() = database.agentRunEventDao()
    private val sanitizer = TracePayloadSanitizer()

    suspend fun createRun(request: CreateAgentRun): AgentRun = writeMutex.withLock {
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
            metadataJson = request.metadata?.toString()?.take(4 * 1024),
        )
        database.withTransaction {
            runDao.insert(run)
            eventDao.insert(
                event = AgentRunEvent(
                    id = Uuid.random().toString(),
                    runId = run.id,
                    sequence = 0,
                    type = AgentRunEventType.RUN_CREATED.name,
                    createdAtMs = now,
                    severity = TraceSeverity.INFO.name,
                ),
            )
        }
        run
    }

    suspend fun append(runId: String, event: NewAgentRunEvent): AgentRunEvent = writeMutex.withLock {
        database.withTransaction {
            val run = checkNotNull(runDao.getById(runId)) { "Agent run does not exist: $runId" }
            check(!AgentRunStatus.fromName(run.status).isTerminal) {
                "Agent run is already terminal: $runId"
            }
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

    suspend fun finish(runId: String, outcome: AgentRunOutcome): FinishRunResult = writeMutex.withLock {
        database.withTransaction {
            val run = runDao.getById(runId) ?: return@withTransaction FinishRunResult.Missing
            if (AgentRunStatus.fromName(run.status).isTerminal) {
                return@withTransaction FinishRunResult.AlreadyTerminal
            }
            val now = System.currentTimeMillis()
            val eventType = when (outcome.status) {
                AgentRunStatus.succeeded -> AgentRunEventType.RUN_COMPLETED
                AgentRunStatus.failed -> AgentRunEventType.RUN_FAILED
                AgentRunStatus.cancelled -> AgentRunEventType.RUN_CANCELLED
                else -> return@withTransaction FinishRunResult.InvalidOutcome
            }
            eventDao.insert(
                AgentRunEvent(
                    id = Uuid.random().toString(),
                    runId = runId,
                    sequence = eventDao.findNextSequence(runId),
                    type = eventType.name,
                    createdAtMs = now,
                    severity = if (eventType == AgentRunEventType.RUN_FAILED) TraceSeverity.ERROR.name else TraceSeverity.INFO.name,
                    summary = outcome.terminalReason?.take(MAX_SUMMARY_CHARS),
                ),
            )
            runDao.update(
                run.copy(
                    status = outcome.status.name,
                    updatedAtMs = now,
                    finishedAtMs = now,
                    lastError = outcome.terminalReason?.take(MAX_SUMMARY_CHARS) ?: run.lastError,
                ),
            )
            FinishRunResult.Finished
        }
    }

    fun observeRun(runId: String): Flow<AgentRun?> = runDao.observeById(runId)

    fun observeEvents(runId: String): Flow<List<AgentRunEvent>> = eventDao.findByRun(runId)

    private fun statusFor(type: AgentRunEventType, current: String): String = when (type) {
        AgentRunEventType.RUN_STARTED -> AgentRunStatus.running.name
        AgentRunEventType.RUN_COMPLETED -> AgentRunStatus.succeeded.name
        AgentRunEventType.RUN_FAILED -> AgentRunStatus.failed.name
        AgentRunEventType.RUN_CANCELLED -> AgentRunStatus.cancelled.name
        else -> current
    }

    private companion object {
        const val MAX_SUMMARY_CHARS = 2_000
    }
}

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
