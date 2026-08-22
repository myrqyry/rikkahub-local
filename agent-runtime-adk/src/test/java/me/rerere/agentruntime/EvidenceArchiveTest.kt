package me.rerere.agentruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceArchiveTest {

    private fun archive() = EvidenceArchive()

    @Test
    fun `record stores evidence with provenance and type`() {
        val a = archive()
        val record = EvidenceRecord(
            id = "rec-1",
            type = "trajectory",
            payload = "{\"steps\":2}",
            provenance = ProvenanceAnchor(origin = "claude", sessionId = "sess-9"),
        )
        a.put(record)
        val got = a.get("rec-1")
        assertNotNull(got)
        assertEquals("trajectory", got?.type)
        assertEquals("claude", got?.provenance?.origin)
        assertEquals("sess-9", got?.provenance?.sessionId)
    }

    @Test
    fun `get returns null for unknown id`() {
        assertNull(archive().get("nope"))
    }

    @Test
    fun `records can be queried by type`() {
        val a = archive()
        a.put(EvidenceRecord("t1", "trajectory", "{\"steps\":1}", ProvenanceAnchor("claude", "s1")))
        a.put(EvidenceRecord("e1", "evaluation", "{\"score\":0.9}", ProvenanceAnchor("claude", "s1")))
        a.put(EvidenceRecord("t2", "trajectory", "{\"steps\":3}", ProvenanceAnchor("codex", "s2")))
        assertEquals(listOf("t1", "t2"), a.query(type = "trajectory").map { it.id })
        assertEquals(listOf("e1"), a.query(type = "evaluation").map { it.id })
    }

    @Test
    fun `records can be queried by origin`() {
        val a = archive()
        a.put(EvidenceRecord("t1", "trajectory", "{}", ProvenanceAnchor("claude", "s1")))
        a.put(EvidenceRecord("t2", "trajectory", "{}", ProvenanceAnchor("codex", "s2")))
        assertEquals(listOf("t1"), a.query(origin = "claude").map { it.id })
    }

    @Test
    fun `archive accepts trajectory records and evaluation results`() {
        val a = archive()
        val trajectory = TrajectoryRecorder().let { recorder ->
            recorder.record(
                "look",
                EnvironmentResult(
                    observation = Observation(rendered = "after: look"),
                    outcome = Outcome.Success,
                    done = false,
                    metrics = mapOf("steps" to 1.0),
                ),
            )
            recorder.trajectory()
        }
        a.put(EvidenceRecord("traj-1", "trajectory", trajectory.describe(), ProvenanceAnchor("meristem", "run-1")))

        val result = Evaluator<String, String> { case -> case.input.uppercase() }
            .evaluate(EvaluationCase(input = "hello"))
        a.put(EvidenceRecord("eval-1", "evaluation", "output=${result.output}", ProvenanceAnchor("meristem", "run-1")))

        assertEquals(2, a.query(origin = "meristem").size)
        assertTrue(a.query(type = "trajectory").first().payload.contains("#1 look"))
        assertTrue(a.query(type = "evaluation").first().payload.contains("HELLO"))
    }

    @Test
    fun `external session evidence is stored with its origin`() {
        val a = archive()
        a.put(EvidenceRecord("ext-1", "session", "opaque-payload", ProvenanceAnchor(origin = "codex", sessionId = "ext-sess-77")))
        val got = a.get("ext-1")
        assertEquals("opaque-payload", got?.payload)
        assertEquals("ext-sess-77", got?.provenance?.sessionId)
    }
}
