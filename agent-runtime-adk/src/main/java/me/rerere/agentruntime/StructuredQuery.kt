package me.rerere.agentruntime

/**
 * Langfun-style structured query. Carries the SEMANTIC SCHEMA (what the output
 * must look like) independent of the communication protocol used to talk to a
 * model. T is the output type; this is a typed schema carrier, not a prompt
 * template.
 *
 * Langfun's equivalent is `LfQuery` + the `schema` attribute of `lf.query`;
 * the protocol→version→structure mapping (`_OOP_PROMPT_MAP`) is the reason the
 * two axes are kept separate here (semantic schema ≠ communication protocol).
 */
data class StructuredQuery<T>(
    val prompt: String,
    val schema: String,
)

/**
 * The communication protocol: the wire format / prompt-shape identifier used
 * to serialize a [StructuredQuery] for a model (e.g. "json", "python"). This
 * is intentionally separate from the query's semantic schema.
 */
data class StructuredProtocol(
    val name: String,
    val version: String,
) {
    fun id(): String = "$name:$version"
}

/**
 * A fully compiled model request: the protocol plus the serialized
 * schema-bearing payload, ready to hand to a live model or runtime.
 */
data class CompiledModelRequest(
    val protocol: String,
    val compiledPayload: String,
)

/**
 * Compiles a [StructuredQuery] into a [CompiledModelRequest] under a given
 * [StructuredProtocol]. PURE and deterministic: compilation must never touch a
 * model, runtime, or network (compile request ≠ execute request — execution
 * needs a live LM/AgentRuntime; compilation does not).
 */
object QueryCompiler {
    fun compile(query: StructuredQuery<*>, protocol: StructuredProtocol): CompiledModelRequest =
        CompiledModelRequest(
            protocol = protocol.id(),
            compiledPayload = buildString {
                append("PROTOCOL ")
                append(protocol.id())
                append('\n')
                append("SCHEMA ")
                append(query.schema)
                append('\n')
                append(query.prompt)
            },
        )
}

/**
 * A single evaluation case: the input plus an optional ground truth. Mirrors
 * Langfun's `Example` (id/input/output/error/metadata) in its minimal form.
 */
data class EvaluationCase<I, O>(
    val input: I,
    val groundTruth: O? = null,
    val metadata: Map<String, Any?> = emptyMap(),
)

/**
 * The deterministic result of running one [EvaluationCase]. Mirrors Langfun's
 * Example.output/error/metric_metadata plus the EvaluationState comparison.
 */
data class EvaluationResult<O>(
    val output: O?,
    val error: String?,
    val matchesGroundTruth: Boolean,
    val metadata: Map<String, Any?> = emptyMap(),
)

/**
 * Evaluates [EvaluationCase]s deterministically. Mirrors Langfun's `Evaluation`
 * base (process(example) → output; ground truth comparison) in its minimal
 * form. The multi-model experiment matrix / checkpointing runner is a later
 * Langfun slice.
 */
class Evaluator<I, O>(
    private val process: (EvaluationCase<I, O>) -> O,
) {
    fun evaluate(case: EvaluationCase<I, O>): EvaluationResult<O> {
        val output = try {
            process(case)
        } catch (e: Exception) {
            return EvaluationResult(
                output = null,
                error = e.message ?: "evaluation failed",
                matchesGroundTruth = false,
                metadata = case.metadata + ("input" to case.input),
            )
        }
        return EvaluationResult(
            output = output,
            error = null,
            matchesGroundTruth = case.groundTruth != null && case.groundTruth == output,
            metadata = case.metadata + ("input" to case.input),
        )
    }
}
