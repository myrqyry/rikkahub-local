package me.rerere.locallm.litert

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema

/**
 * Phase C1 — deterministically generates the [ShadowCandidate] set for a single
 * primary [ActionPlan], so the [ShadowCandidateEvaluator] can rank them and the
 * best (never merely the first) wins.
 *
 * Candidate sources generated here mirror the compiler's lossless repairs but are
 * proposed as *independent* variants rather than a single rewrite chain:
 *
 *  - [ShadowCandidate.Source.PRIMARY] — the raw plan, unchanged.
 *  - [ShadowCandidate.Source.ALIAS_NORMALIZED] — a deprecated tool id replaced
 *    by its canonical form via [CompilationContext.canonicalAliases] (lossless).
 *  - [ShadowCandidate.Source.DEFAULT_FILLED] — missing required arguments filled
 *    from schema defaults (lossless).
 *  - [ShadowCandidate.Source.COERCED_ARGS] — string arguments losslessly coerced
 *    to their schema types (lossless).
 *
 * No tool is executed and the model is never invoked here — the planner is pure
 * and deterministic. A plan that needs a *lossy* repair is deliberately NOT
 * proposed (that must go back to the model / user for approval); it will be
 * surfaced by the evaluator only if it is the primary input and every candidate
 * is invalid.
 *
 * [plannerId] is a stable id prefix used for deterministic tie-breaking.
 */
class CandidatePlanner(
    private val plannerId: String = "candidate",
) {

    /**
     * Propose candidates for [plan] given [context]. Always includes the primary
     * plan (source [ShadowCandidate.Source.PRIMARY]); alias/default/coercion
     * variants are appended only when they actually differ from the input.
     */
    fun propose(plan: ActionPlan, context: CompilationContext): List<ShadowCandidate> {
        return when (plan) {
            is ActionPlan.ToolCall -> proposeToolCall(plan, context)
            is ActionPlan.WorkflowCall -> listOf(ShadowCandidate("$plannerId-primary", plan, ShadowCandidate.Source.PRIMARY))
            is ActionPlan.ProcedureCall -> listOf(ShadowCandidate("$plannerId-primary", plan, ShadowCandidate.Source.PRIMARY))
        }
    }

    private fun proposeToolCall(plan: ActionPlan.ToolCall, context: CompilationContext): List<ShadowCandidate> {
        val out = mutableListOf(ShadowCandidate("$plannerId-primary", plan, ShadowCandidate.Source.PRIMARY))

        // ALIAS_NORMALIZED — canonical tool id, when the input is a resolving alias.
        val canonical = context.canonicalAliases[plan.toolName]
        val resolvedTool = canonical?.let(context.toolCatalog::get)
        if (canonical != null && resolvedTool != null) {
            val normalized = plan.copy(toolName = canonical)
            out += ShadowCandidate("$plannerId-alias", normalized, ShadowCandidate.Source.ALIAS_NORMALIZED)
        }

        val schema = resolvedTool?.parameters() ?: context.toolCatalog[plan.toolName]?.parameters()
        if (schema is InputSchema.Obj) {
            // DEFAULT_FILLED — missing required args filled from schema defaults.
            val filled = fillDefaults(plan, schema)
            if (filled != null) {
                out += ShadowCandidate("$plannerId-default", filled, ShadowCandidate.Source.DEFAULT_FILLED)
            }
            // COERCED_ARGS — lossless string→typed coercion.
            val coerced = coerceArgs(plan, schema)
            if (coerced != null) {
                out += ShadowCandidate("$plannerId-coerce", coerced, ShadowCandidate.Source.COERCED_ARGS)
            }
        }

        return out
    }

    /** Fill missing required args from schema defaults; null when nothing changes. */
    private fun fillDefaults(plan: ActionPlan.ToolCall, schema: InputSchema.Obj): ActionPlan.ToolCall? {
        val required = schema.required ?: emptyList()
        val missing = required.filter { !plan.args.containsKey(it) }
        val fillable = missing.filter { key ->
            val def = (schema.properties[key] as? JsonObject)?.get("default")
            def != null && def !is JsonNull
        }
        if (fillable.isEmpty()) return null
        return plan.copy(
            args = buildJsonObject {
                for ((k, v) in plan.args) put(k, v)
                for (k in fillable) put(k, (schema.properties[k] as JsonObject)["default"]!!)
            },
        )
    }

    /** Coerce string args to schema types where lossless; null when nothing changes. */
    private fun coerceArgs(plan: ActionPlan.ToolCall, schema: InputSchema.Obj): ActionPlan.ToolCall? {
        val props = schema.properties
        val coerced = buildJsonObject {
            for ((key, value) in plan.args) {
                val prop = props[key] as? JsonObject
                val c = if (prop == null) null else coerce(value, prop)
                put(key, c ?: value)
            }
        }
        if (coerced == plan.args) return null
        return plan.copy(args = coerced)
    }

    private fun coerce(value: JsonElement, prop: JsonObject): JsonElement? {
        if (value is JsonNull) return null
        val primitive = value as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        val type = (prop["type"] as? JsonPrimitive)?.content ?: return null
        return when (type) {
            "integer" -> primitive.content.toLongOrNull()?.let { JsonPrimitive(it) }
            "number" -> primitive.content.toDoubleOrNull()?.let { JsonPrimitive(it) }
            "boolean" -> when (primitive.content) {
                "true" -> JsonPrimitive(true)
                "false" -> JsonPrimitive(false)
                else -> null
            }
            else -> null
        }
    }
}
