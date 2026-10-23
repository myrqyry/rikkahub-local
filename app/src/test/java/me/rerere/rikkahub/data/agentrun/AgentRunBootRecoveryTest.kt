package me.rerere.rikkahub.data.agentrun

import kotlinx.coroutines.runBlocking
import me.rerere.agentruntime.ContinuationCheckpointDraft
import me.rerere.agentruntime.ContinuationSnapshot
import me.rerere.agentruntime.InMemoryContinuationStore
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 24 — JVM unit tests for the boot-recovery sweep logic.
 *
 * [AgentRunBootRecovery] takes an injectable `notifyStranded` sink — the production
 * constructor wires it to a real Android notification, but the primary constructor lets a
 * JVM test pass a capturing lambda. We assert on the count flipped, the resulting ledger
 * state, and that the notifier fires exactly once with the stranded rows.
 */
class AgentRunBootRecoveryTest {

    private val now = System.currentTimeMillis()
    private val stale = now - 31L * 60_000L
    // A separate new run created after the current process started.
    private val fresh = now + 5_000L

    private var notifiedWith: List<AgentRun>? = null
    private var notifyCount = 0
    private var recoveryCandidates: List<AgentRunRecoveryCandidate>? = null

    private fun recovery(repo: AgentRunRepository): AgentRunBootRecovery =
        AgentRunBootRecovery(repository = repo, notifyRecovery = { report ->
            notifyCount++
            notifiedWith = report.stranded
        })

    private fun recovery(
        repo: AgentRunRepository,
        store: InMemoryContinuationStore,
    ): AgentRunBootRecovery = AgentRunBootRecovery(
        repository = repo,
        continuationStore = store,
        notifyRecovery = { report ->
            notifyCount++
            notifiedWith = report.stranded
            recoveryCandidates = report.candidates
        },
    )

    @Test
    fun `prior-process in-flight rows are flipped to process_lost`() = runBlocking {
        val dao = FakeAgentRunDao()
        val repo = AgentRunRepository(dao)
        // Prior-process run, still in-flight after restart.
        dao.insert(
            runRow(id = "stranded-1", status = AgentRunStatus.running, updatedAtMs = stale)
        )
        dao.insert(
            runRow(id = "stranded-2", status = AgentRunStatus.awaiting_approval, updatedAtMs = stale)
        )

        val flipped = recovery(repo).runRecovery(now)

        assertEquals(2, flipped)
        assertEquals(AgentRunStatus.process_lost.name, repo.getById("stranded-1")!!.status)
        assertEquals(AgentRunStatus.process_lost.name, repo.getById("stranded-2")!!.status)
        // Exactly ONE aggregate notification for the whole sweep — no per-row storm.
        assertEquals("exactly one aggregate notification", 1, notifyCount)
        assertEquals(2, notifiedWith!!.size)
    }

    @Test
    fun `fresh in-flight rows are NOT swept`() = runBlocking {
        val dao = FakeAgentRunDao()
        val repo = AgentRunRepository(dao)
        // A run that updated recently — a live process owns it, leave it alone.
        dao.insert(runRow(id = "live-1", status = AgentRunStatus.running, updatedAtMs = fresh))

        val flipped = recovery(repo).runRecovery(now)

        assertEquals(0, flipped)
        assertEquals(AgentRunStatus.running.name, repo.getById("live-1")!!.status)
        assertEquals("no notification when nothing was flipped", 0, notifyCount)
    }

    @Test
    fun `a recent run left by the previous process is recovered at startup`() = runBlocking {
        val dao = FakeAgentRunDao()
        val repo = AgentRunRepository(dao)
        dao.insert(runRow(id = "prior-process", status = AgentRunStatus.running, updatedAtMs = now - 5_000L))

        val flipped = recovery(repo).runRecovery(now)

        assertEquals(1, flipped)
        assertEquals(AgentRunStatus.process_lost.name, repo.getById("prior-process")!!.status)
        assertEquals(1, notifyCount)
    }

    @Test
    fun `a run refreshed after the recovery scan is not falsely marked lost`() = runBlocking {
        val backing = FakeAgentRunDao()
        backing.insert(runRow(id = "refreshed", status = AgentRunStatus.running, updatedAtMs = stale))
        backing.insert(runRow(id = "genuinely-lost", status = AgentRunStatus.running, updatedAtMs = stale))

        // Simulate another coroutine refreshing the row between the scan and the final
        // repository transition. The report must contain only runs actually marked lost.
        val dao = object : AgentRunDao by backing {
            override suspend fun findStranded(beforeMs: Long): List<AgentRun> {
                val scanned = backing.findStranded(beforeMs)
                backing.update(backing.getById("refreshed")!!.copy(updatedAtMs = now + 5_000L))
                return scanned
            }
        }
        val repo = AgentRunRepository(dao)

        val flipped = recovery(repo).runRecovery(now)

        assertEquals(1, flipped)
        assertEquals(AgentRunStatus.running.name, repo.getById("refreshed")!!.status)
        assertEquals(AgentRunStatus.process_lost.name, repo.getById("genuinely-lost")!!.status)
        assertEquals(1, notifyCount)
        assertEquals(listOf("genuinely-lost"), notifiedWith!!.map { it.id })
    }

    @Test
    fun `terminal rows are never touched`() = runBlocking {
        val dao = FakeAgentRunDao()
        val repo = AgentRunRepository(dao)
        dao.insert(runRow(id = "done-1", status = AgentRunStatus.succeeded, updatedAtMs = stale))
        dao.insert(runRow(id = "done-2", status = AgentRunStatus.failed, updatedAtMs = stale))

        val flipped = recovery(repo).runRecovery(now)

        assertEquals(0, flipped)
        assertEquals(AgentRunStatus.succeeded.name, repo.getById("done-1")!!.status)
        assertEquals(AgentRunStatus.failed.name, repo.getById("done-2")!!.status)
    }

    @Test
    fun `recovery on an empty ledger flips nothing`() = runBlocking {
        val flipped = recovery(AgentRunRepository(FakeAgentRunDao())).runRecovery(now)
        assertEquals(0, flipped)
    }

    @Test
    fun `mixed ledger - only the stale in-flight rows flip`() = runBlocking {
        val dao = FakeAgentRunDao()
        val repo = AgentRunRepository(dao)
        dao.insert(runRow(id = "stranded", status = AgentRunStatus.running, updatedAtMs = stale))
        dao.insert(runRow(id = "live", status = AgentRunStatus.running, updatedAtMs = fresh))
        dao.insert(runRow(id = "done", status = AgentRunStatus.succeeded, updatedAtMs = stale))
        dao.insert(runRow(id = "queued-stale", status = AgentRunStatus.queued, updatedAtMs = stale))

        val flipped = recovery(repo).runRecovery(now)

        assertEquals(2, flipped)
        assertEquals(AgentRunStatus.process_lost.name, repo.getById("stranded")!!.status)
        assertEquals(AgentRunStatus.process_lost.name, repo.getById("queued-stale")!!.status)
        assertEquals(AgentRunStatus.running.name, repo.getById("live")!!.status)
        assertEquals(AgentRunStatus.succeeded.name, repo.getById("done")!!.status)
    }

    @Test
    fun `recovery exposes only latest workflow checkpoint for a flipped workflow run`() = runBlocking {
        val dao = FakeAgentRunDao()
        val repo = AgentRunRepository(dao)
        val store = InMemoryContinuationStore()
        dao.insert(runRow(id = "workflow", status = AgentRunStatus.running, updatedAtMs = stale)
            .copy(kind = AgentRunKind.Workflow.wire))
        dao.insert(runRow(id = "cron", status = AgentRunStatus.running, updatedAtMs = stale))
        store.append(checkpoint("first", "workflow", "first action"))
        store.append(checkpoint("latest", "workflow", "second action"))

        recovery(repo, store).runRecovery(now)

        assertEquals(1, recoveryCandidates!!.size)
        assertEquals("workflow", recoveryCandidates!!.single().run.id)
        assertEquals("latest", recoveryCandidates!!.single().checkpoint.id)
    }

    private fun checkpoint(id: String, runId: String, action: String) = ContinuationCheckpointDraft(
        id = id,
        runId = runId,
        verifiedAtMs = now,
        snapshot = ContinuationSnapshot(
            goal = "workflow",
            pendingWork = "continue",
            lastVerifiedAction = action,
            verificationState = "verified",
        ),
    )

    private fun runRow(id: String, status: AgentRunStatus, updatedAtMs: Long): AgentRun =
        AgentRun(
            id = id,
            kind = AgentRunKind.Cron.wire,
            domainId = "domain-$id",
            parentRunId = null,
            status = status.name,
            createdAtMs = updatedAtMs,
            updatedAtMs = updatedAtMs,
            startedAtMs = if (status == AgentRunStatus.queued) null else updatedAtMs,
            finishedAtMs = if (status.isTerminal) updatedAtMs else null,
            lastError = null,
            metadataJson = null,
        )
}
