package me.rerere.locallm.litert.zero

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import me.rerere.locallm.litert.Postcondition

/**
 * Phase 7 — typed compound-procedure model for the Zero deterministic execution substrate.
 *
 * A [ZeroProcedure] is a DAG of [ZeroStep]s where later steps may reference the output of
 * earlier steps through `{{stepId.path}}` templates inside their [ZeroStep.args]. The
 * [ZeroProcedureEngine] compiles the graph (duplicate ids, unknown tools, dangling/cyclic
 * references) then executes steps in deterministic topological order, substituting each
 * step's resolved output into the steps that reference it.
 *
 * This is the backend prototype the [me.rerere.locallm.litert.ZeroWorkflowExecutor] seam is
 * meant to route compound `ActionPlan.WorkflowCall`s through, replacing the current flat
 * "list of independent actions" shape ([WorkflowEngineZeroWorkflowExecutor]) with real
 * inter-step data flow. It is deliberately pure-JVM so the substrate is unit-testable
 * without Android.
 */
@Serializable
data class ZeroProcedure(
    val id: String,
    val description: String? = null,
    /** Steps in authoring order. Execution order is derived (topological), not assumed. */
    val steps: List<ZeroStep>,
    /** Stop on the first failed step (true) or continue collecting failures (false). */
    val failFast: Boolean = true,
    /**
     * Deterministic postconditions (roadmap C4) that must hold after successful execution
     * for the run to be considered *verified* success. Execution may succeed while a
     * postcondition fails — a success is distinct from a verified success.
     */
    val postconditions: List<Postcondition> = emptyList(),
)

/**
 * A single deterministic step: one tool call whose [args] may contain `{{stepId.path}}`
 * templates resolving against the text/JSON output of a prior step.
 *
 * Reference syntax: `{{stepId}}` renders the whole prior output; `{{stepId.a.b}}` navigates
 * a dot-path into the prior output after it is parsed as JSON (falls back to the raw string
 * when the output is not valid JSON). Only forward references to steps that resolve before
 * this one are legal — the compiler rejects dangling and cyclic references.
 */
@Serializable
data class ZeroStep(
    val stepId: String,
    val tool: String,
    val args: JsonObject = buildJsonObject { },
    /** Per-step timeout in seconds. Default 60. */
    val timeoutSeconds: Int = 60,
)

/** Compile-time diagnostic for an invalid [ZeroProcedure]. */
@Serializable
data class ZeroDiagnostic(
    val code: String,
    val stepId: String?,
    val message: String,
)

sealed interface ZeroCompilationResult {
    data class Valid(val order: List<String>) : ZeroCompilationResult
    data class Invalid(val diagnostics: List<ZeroDiagnostic>) : ZeroCompilationResult
}

/** Per-step outcome of a [ZeroProcedureEngine.execute] run. */
@Serializable
data class ZeroStepResult(
    val stepId: String,
    val success: Boolean,
    val output: JsonElement? = null,
    val error: String? = null,
)

/**
 * Outcome of running a whole procedure. [outputs] is `stepId -> resolved output` for every
 * step that produced one, so callers can compose procedures from other procedures.
 */
data class ZeroExecutionResult(
    val success: Boolean,
    val stepResults: List<ZeroStepResult>,
    val outputs: Map<String, JsonElement>,
    val error: String? = null,
)
