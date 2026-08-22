package me.rerere.agentruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class AgentEnvironmentTest {

    private class FakeEnvironment : AgentEnvironment {
        var steps = 0
        val observations = mutableListOf<String>()
        override suspend fun reset(): Observation =
            Observation(rendered = "env ready").also { observations.add(it.rendered) }

        override suspend fun step(action: AgentAction): EnvironmentResult {
            steps++
            return EnvironmentResult(
                observation = Observation(rendered = "after: ${action.name}"),
                outcome = Outcome.Success,
                done = steps >= 2,
                metrics = mapOf("steps" to steps.toDouble()),
            )
        }
    }

    @Test
    fun `environment implements reset and step`() = runBlocking {
        val env = FakeEnvironment()
        val obs = env.reset()
        assertEquals("env ready", obs.rendered)
        val r1 = env.step(AgentAction(name = "look", parameters = emptyMap<String, Any>()))
        assertEquals("after: look", r1.observation.rendered)
        assertEquals(Outcome.Success, r1.outcome)
        assertTrue(!r1.done)
        assertEquals(1.0, r1.metrics["steps"]!!, 0.0)
        val r2 = env.step(AgentAction(name = "act", parameters = emptyMap<String, Any>()))
        assertTrue(r2.done)
    }

    @Test
    fun `environment result carries metrics and outcome`() {
        val result = EnvironmentResult(
            observation = Observation(rendered = "x"),
            outcome = Outcome.Failure,
            done = true,
            metrics = mapOf("attempts" to 3.0),
        )
        assertEquals(Outcome.Failure, result.outcome)
        assertTrue(result.done)
        assertEquals(3.0, result.metrics["attempts"]!!, 0.0)
    }

    @Test
    fun `trajectory records sequential steps from an environment`() = runBlocking {
        val env = FakeEnvironment()
        val recorder = TrajectoryRecorder()
        env.reset()
        val r1 = env.step(AgentAction(name = "look", parameters = emptyMap<String, Any>()))
        recorder.record("look", r1)
        val r2 = env.step(AgentAction(name = "act", parameters = emptyMap<String, Any>()))
        recorder.record("act", r2)
        val trajectory = recorder.trajectory()
        assertEquals(2, trajectory.steps.size)
        assertEquals(1, trajectory.steps[0].stepId)
        assertEquals(2, trajectory.steps[1].stepId)
        assertEquals("look", trajectory.steps[0].actionName)
        assertEquals(Outcome.Success, trajectory.steps[0].outcome)
        assertTrue(trajectory.steps[1].done)
    }

    @Test
    fun `trajectory renders a deterministic summary`() = runBlocking {
        val env = FakeEnvironment()
        val recorder = TrajectoryRecorder()
        env.reset()
        val r = env.step(AgentAction(name = "ping", parameters = emptyMap<String, Any>()))
        recorder.record("ping", r)
        val summary = recorder.trajectory().describe()
        assertTrue(summary.contains("1"))
        assertTrue(summary.contains("ping"))
        assertTrue(summary.contains("Success"))
    }
}
