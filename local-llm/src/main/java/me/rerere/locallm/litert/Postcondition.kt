package me.rerere.locallm.litert

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.locallm.litert.zero.OutputPath
import me.rerere.locallm.litert.zero.StepOutputRef

/**
 * Deterministic postconditions (roadmap C4). A tool returning normally is NOT the same
 * as the goal being accomplished; a verifier checks the world/artifact actually moved.
 *
 * This is deliberately a small catalog — workspace/browser-specific conditions come later
 * in E/I. All conditions are pure and deterministic: no tools, no model calls.
 */
@Serializable
sealed interface Postcondition {
    /** The execution itself completed (weakest condition; distinguishes nothing beyond receipt). */
    @Serializable
    data object ExecutionSucceeded : Postcondition

    /** A referenced output produced by a step exists. */
    @Serializable
    data class OutputPathExists(
        val ref: StepOutputRef,
    ) : Postcondition

    /** A referenced output equals an expected JSON value exactly. */
    @Serializable
    data class OutputEquals(
        val ref: StepOutputRef,
        val expected: JsonElement,
    ) : Postcondition

    /** A step's output carries a reported field equal to the expected JSON value. */
    @Serializable
    data class ToolReported(
        val stepId: String,
        val field: String,
        val expected: JsonElement,
    ) : Postcondition
}

/**
 * Verifies [Postcondition]s against the actual outputs of an executed procedure.
 * Pure and deterministic. Returns a verdict; a failed verdict is distinct from a
 * failed execution — execution may succeed while a postcondition fails.
 */
class PostconditionVerifier {

    /**
     * Verify [postconditions] against [outputs] (map: stepId -> step output JSON).
     * [RefError] when the verifier cannot resolve a reference; false when a check fails.
     */
    fun verify(
        postconditions: List<Postcondition>,
        outputs: Map<String, JsonElement>,
    ): PostconditionResult {
        if (postconditions.isEmpty()) return PostconditionResult.Passed
        for (pc in postconditions) {
            when (pc) {
                Postcondition.ExecutionSucceeded -> Unit
                is Postcondition.OutputPathExists -> {
                    val value = resolve(pc.ref, outputs)
                    if (value == null) return PostconditionResult.Failed(
                        "output missing", "no output at '${pc.ref}'",
                    )
                }
                is Postcondition.OutputEquals -> {
                    val value = resolve(pc.ref, outputs)
                    if (value == null) return PostconditionResult.Failed(
                        "output missing", "no output at '${pc.ref}'",
                    )
                    if (value != pc.expected) return PostconditionResult.Failed(
                        "output mismatch",
                        "'${pc.ref}' expected ${pc.expected} but was $value",
                    )
                }
                is Postcondition.ToolReported -> {
                    val output = outputs[pc.stepId]
                        ?: return PostconditionResult.Failed(
                            "output missing", "no output for step '${pc.stepId}'",
                        )
                    val reported = reportedField(output, pc.field)
                    if (reported == null) return PostconditionResult.Failed(
                        "field missing", "step '${pc.stepId}' has no field '${pc.field}'",
                    )
                    if (reported != pc.expected) return PostconditionResult.Failed(
                        "field mismatch",
                        "step '${pc.stepId}'.${pc.field} expected ${pc.expected} but was $reported",
                    )
                }
            }
        }
        return PostconditionResult.Passed
    }

    private fun resolve(ref: StepOutputRef, outputs: Map<String, JsonElement>): JsonElement? {
        val root = outputs[ref.stepId] ?: return null
        if (ref.path is OutputPath.Root) return root
        val segments = (ref.path as OutputPath.Field).segments
        var current: JsonElement = root
        for (segment in segments) {
            current = when (current) {
                is JsonObject -> current[segment] ?: return null
                is kotlinx.serialization.json.JsonArray ->
                    segment.toIntOrNull()?.let { current.getOrNull(it) } ?: return null
                else -> return null
            }
        }
        return current
    }

    private fun reportedField(output: JsonElement, field: String): JsonElement? {
        if (output !is JsonObject) return null
        return output[field]
    }
}

/** Verdict of a postcondition check. */
sealed interface PostconditionResult {
    data object Passed : PostconditionResult
    data class Failed(val code: String, val detail: String) : PostconditionResult
}

/** Convenience: build an [OutputEquals] postcondition comparing to a string. */
fun outputEqualsText(stepId: String, path: List<String>, expected: String): Postcondition =
    Postcondition.OutputEquals(StepOutputRef(stepId, pathOrRoot(path)), JsonPrimitive(expected))

private fun pathOrRoot(path: List<String>): OutputPath =
    if (path.isEmpty()) OutputPath.Root else OutputPath.Field(path)
