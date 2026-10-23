package me.rerere.rikkahub.data.agentrun

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.rerere.agentruntime.ContinuationCheckpoint
import me.rerere.agentruntime.ContinuationStore

private const val TAG = "AgentRunBootRecovery"

/** A stranded workflow run paired with its latest manual-resume checkpoint. */
data class AgentRunRecoveryCandidate(
    val run: AgentRun,
    val checkpoint: ContinuationCheckpoint,
)

/** Recovery output for a host that wants to present manual continuation options. */
data class AgentRunRecoveryReport(
    val stranded: List<AgentRun>,
    val candidates: List<AgentRunRecoveryCandidate>,
)

/**
 * Phase 24 — cross-pillar boot-recovery sweep for the unified `agent_runs` ledger.
 *
 * Run once per process start from [me.rerere.rikkahub.RikkaHubApp.onCreate], after Koin
 * init. The ledger is the source of truth for prior-process in-flight work: a row still
 * `running` / `awaiting_approval` / `queued` last updated before this process started
 * cannot be owned by the new process. A fixed age threshold misses recent crashes and
 * cannot establish liveness for long-running work.
 *
 * Stranded rows are flipped to [AgentRunStatus.process_lost] and a SINGLE aggregate
 * notification is posted (no per-row storm) — mirrors the Phase 9.5 cron stranded-row
 * sweep, generalised across all five autonomous paths.
 *
 * Best-effort throughout: a stuck or slow app start is worse than a skipped recovery
 * sweep, so failures are logged and never thrown.
 */
class AgentRunBootRecovery(
    private val repository: AgentRunRepository,
    private val continuationStore: ContinuationStore? = null,
    /**
     * Aggregate-notification sink. Defaults to a real Android notification; injectable so
     * the sweep logic can be unit-tested on the JVM without a [Context]. Called at most
     * once per [runRecovery] invocation, only when at least one row was flipped.
     */
    private val notifyRecovery: (AgentRunRecoveryReport) -> Unit = {},
) {

    /** Convenience constructor for production wiring — posts a real Android notification. */
    constructor(
        context: Context,
        repository: AgentRunRepository,
        continuationStore: ContinuationStore,
    ) : this(
        repository = repository,
        continuationStore = continuationStore,
        notifyRecovery = { report -> postAggregateNotification(context, report) },
    )

    /**
     * Mark in-flight rows last updated before [processStartedAtMs] as lost. The caller
     * captures the start time before scheduling any new work in this process.
     * Never replay effects automatically; only report checkpoints for manual review.
     */
    suspend fun runRecovery(processStartedAtMs: Long): Int {
        return runCatching {
            val stranded = repository.getStranded(processStartedAtMs)
            if (stranded.isEmpty()) {
                logSafe { Log.i(TAG, "runRecovery: no stranded runs") }
                return@runCatching 0
            }
            val flipped = repository.markAllProcessLost(stranded.map { it.id }, processStartedAtMs)
            logSafe { Log.w(TAG, "runRecovery: flipped ${flipped.size} stranded run(s) to process_lost") }
            if (flipped.isNotEmpty()) {
                val candidates = flipped.mapNotNull { run ->
                    if (run.kind != AgentRunKind.Workflow.wire) return@mapNotNull null
                    continuationStore?.latest(run.id)?.let { AgentRunRecoveryCandidate(run, it) }
                }
                runCatching { notifyRecovery(AgentRunRecoveryReport(flipped, candidates)) }
                    .onFailure { logSafe { Log.w(TAG, "notifyRecovery failed", it) } }
            }
            flipped.size
        }.onFailure { logSafe { Log.w(TAG, "runRecovery failed", it) } }
            .getOrDefault(0)
    }

    companion object {
        const val CHANNEL_ID = "rikkahub_agent_run_recovery"

        /** Fixed id so subsequent boots replace, not stack, the aggregate notification. */
        private const val AGGREGATE_NOTIF_ID = Int.MAX_VALUE - 101

        /**
         * One notification for the whole sweep, summarised by kind — never one per row.
         * Fixed notification id so a subsequent boot replaces the prior aggregate rather
         * than stacking them.
         */
        private fun postAggregateNotification(context: Context, report: AgentRunRecoveryReport) {
            runCatching {
                val stranded = report.stranded
                val nm = context.getSystemService(NotificationManager::class.java) ?: return
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            "Autonomous run recovery",
                            NotificationManager.IMPORTANCE_DEFAULT,
                        )
                    )
                }
                val byKind = stranded.groupingBy { AgentRunKind.fromWire(it.kind)?.wire ?: it.kind }
                    .eachCount()
                val breakdown = byKind.entries.joinToString(", ") { "${it.value} ${it.key}" }
                val title = if (stranded.size == 1) {
                    "An autonomous run was interrupted"
                } else {
                    "${stranded.size} autonomous runs were interrupted"
                }
                val recoveryText = if (report.candidates.isEmpty()) "" else
                    " ${report.candidates.size} workflow checkpoint(s) are available for manual continuation; nothing was replayed."
                val text = "The app was killed mid-run ($breakdown)." + recoveryText + " " +
                    "If this keeps happening, check the battery whitelist and foreground service settings."
                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setAutoCancel(true)
                NotificationManagerCompat.from(context).notify(AGGREGATE_NOTIF_ID, builder.build())
            }.onFailure {
                // POST_NOTIFICATIONS not granted, or notifications restricted — non-fatal.
                logSafe { Log.w(TAG, "postAggregateNotification failed", it) }
            }
        }
    }
}

/**
 * Run a [android.util.Log] call inside a guard so JVM unit tests — where `android.util.Log`
 * is an unmocked stub that throws — don't crash before the code under test can return.
 * Mirrors `WorkflowActionRunner.logSafe`.
 */
private inline fun logSafe(block: () -> Unit) {
    runCatching { block() }
}
