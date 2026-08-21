package me.rerere.agentruntime

/**
 * Tunix-style agent environment. Mirrors Tunix's `BaseEnv`/`BaseTaskEnv`
 * (reset/step with an EnvStepResult carrying observation, reward/done, and
 * info) shaped for Meristem's agent runtime.
 *
 * Langfun lesson applied: `AgentEnvironment` describes the WORLD and its
 * capabilities only — it does not run the agent. Execution is the caller's
 * job (via [AgentRuntime]); the environment just answers "what state am I in"
 * (reset) and "what happened when I acted" (step).
 */
interface AgentEnvironment {
    suspend fun reset(): Observation
    suspend fun step(action: AgentAction): EnvironmentResult
}

/** A structured, renderable snapshot of the environment's state. */
data class Observation(val rendered: String, val structured: Map<String, Any?> = emptyMap())

/** An action the agent takes in the environment. */
data class AgentAction(
    val name: String,
    val parameters: Map<String, Any?> = emptyMap(),
)

/** What the environment reports after an action — mirrors Tunix EnvStepResult. */
data class EnvironmentResult(
    val observation: Observation,
    val outcome: Outcome,
    val done: Boolean,
    val metrics: Map<String, Double>,
)

enum class Outcome { Success, Failure }

/**
 * ATIF-style trajectory recorder. Records the actual action-invocation chain
 * ("what actually happened") as sequentially numbered steps, the destination
 * for the AgentView evidence archive and the Evaluator/Tunix later. ATIF's
 * Step fields (step_id ordinal, source, message, tool_calls, observation,
 * metrics) are kept intentionally small here; subagent trajectories are a
 * later addition.
 */
class TrajectoryRecorder {
    private val steps = mutableListOf<RecordedStep>()

    fun record(actionName: String, result: EnvironmentResult) {
        steps.add(
            RecordedStep(
                stepId = steps.size + 1,
                actionName = actionName,
                observation = result.observation,
                outcome = result.outcome,
                done = result.done,
                metrics = result.metrics,
            )
        )
    }

    fun trajectory(): Trajectory = Trajectory(steps.toList())

    data class RecordedStep(
        val stepId: Int,
        val actionName: String,
        val observation: Observation,
        val outcome: Outcome,
        val done: Boolean,
        val metrics: Map<String, Double>,
    )

    data class Trajectory(val steps: List<RecordedStep>) {
        fun describe(): String =
            steps.joinToString("\n") { s ->
                "#${s.stepId} ${s.actionName} -> ${s.outcome}${if (s.done) " (done)" else ""}"
            }
    }
}
