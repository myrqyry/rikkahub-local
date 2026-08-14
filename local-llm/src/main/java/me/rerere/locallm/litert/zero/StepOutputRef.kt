package me.rerere.locallm.litert.zero

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Typed reference to an output produced by a prior step, compiled from the
 * serialized `{{stepId.path}}` syntax. Keeps the wire syntax user-facing and
 * serializable, but normalizes references into a typed form once at compile
 * time so the executor does not reparse template strings at runtime.
 *
 * Roadmap B9: keep `{{stepId.path}}` as the serialized/user-facing syntax,
 * compile it internally into [StepOutputRef], stop reparsing at runtime.
 */
@Serializable
data class StepOutputRef(
    val stepId: String,
    val path: OutputPath = OutputPath.Root,
) {
    override fun toString(): String = if (path is OutputPath.Root) {
        "{{$stepId}}"
    } else {
        val segments = (path as OutputPath.Field).segments
        "{{$stepId.${segments.joinToString(".")}}}"
    }
}

/** Navigation path within a prior step's output. */
@Serializable
sealed interface OutputPath {
    /** The whole prior output, i.e. `{{stepId}}`. */
    @Serializable
    data object Root : OutputPath

    /** A dot-navigation path, i.e. `{{stepId.a.b}}` or `{{stepId.0}}`. */
    @Serializable
    data class Field(val segments: List<String>) : OutputPath
}

private val refPattern = Regex("""\{\{\s*([A-Za-z0-9_\-]+(?:\.[A-Za-z0-9_\-]+)*)\s*}}""")

/** Parse a single `{{stepId.path}}` token into a typed [StepOutputRef], or null if not a ref. */
fun parseStepOutputRef(token: String): StepOutputRef? {
    val m = refPattern.matchEntire(token.trim()) ?: return null
    val raw = m.groupValues[1]
    val dot = raw.indexOf('.')
    if (dot < 0) return StepOutputRef(raw, OutputPath.Root)
    val stepId = raw.substring(0, dot)
    val rest = raw.substring(dot + 1)
    if (rest.isEmpty()) return StepOutputRef(stepId, OutputPath.Root)
    return StepOutputRef(stepId, OutputPath.Field(rest.split('.')))
}

/** Extract every [StepOutputRef] referenced anywhere inside [element]. */
fun collectStepOutputRefs(element: JsonElement): List<StepOutputRef> {
    val out = ArrayList<StepOutputRef>()
    fun walk(el: JsonElement) {
        when (el) {
            is JsonObject -> el.forEach { (_, v) -> walk(v) }
            is kotlinx.serialization.json.JsonArray -> el.forEach { walk(it) }
            is JsonPrimitive -> {
                if (el !is JsonNull && el.content.contains("{{")) {
                    refPattern.findAll(el.content).forEach { m ->
                        parseStepOutputRef(m.value)?.let { out += it }
                    }
                }
            }
        }
    }
    walk(element)
    return out
}

/**
 * Compile a procedure's serialized argument templates into typed refs.
 * Returns a map from stepId -> the [StepOutputRef]s that step depends on.
 * The serialized [ZeroProcedure] is unchanged; this is the internal typed form.
 */
fun compileStepOutputRefs(procedure: ZeroProcedure): Map<String, List<StepOutputRef>> {
    return procedure.steps.associate { step ->
        step.stepId to collectStepOutputRefs(step.args)
    }
}
