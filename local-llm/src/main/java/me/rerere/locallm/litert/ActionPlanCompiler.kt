package me.rerere.locallm.litert

import me.rerere.ai.core.Tool

/**
 * Phase 3 — the deterministic boundary between what the model proposes and what
 * actually executes.
 *
 * Pipeline invariant: models propose; the compiler normalises and verifies;
 * execution only ever sees compiled plans.
 *
 *    Model output
 *       ↓
 *    ActionPlan
 *       ↓ structural validation
 *       ↓ semantic validation
 *       ↓ deterministic (lossless) repair
 *       ↓ revalidate
 *       ↓
 *    only then: execution / LLM repair / rejection
 *
 * Lossless repairs (canonical aliases, safe argument coercions, defaults) are
 * applied automatically and reported on [CompilationResult.Repaired]. Lossy or
 * unresolvable issues surface as [CompilationResult.Invalid] with
 * [Diagnostic]s and [RepairHint]s so the caller can send them back to the model
 * or require explicit approval — they never silently cross into execution.
 */
interface ActionPlanCompiler {
    suspend fun compile(
        plan: ActionPlan,
        context: CompilationContext,
    ): CompilationResult
}

/**
 * Everything the compiler needs to validate a plan. Tools carry their own
 * [me.rerere.ai.core.InputSchema] via [Tool.parameters], so no separate schema
 * registry is required.
 *
 * @param toolCatalog canonical tool id → [Tool]. A missing id means "unknown tool".
 * @param canonicalAliases aliases (deprecated / legacy ids) → canonical id. A plan
 *   referencing an alias is repaired losslessly to the canonical id when the
 *   canonical tool exists in [toolCatalog].
 */
data class CompilationContext(
    val toolCatalog: Map<String, Tool>,
    val canonicalAliases: Map<String, String> = emptyMap(),
)

/**
 * A single applied (lossless) or pending (lossy) fix. Mirrors the json-render
 * repair contract: [lossy] fixes must never be applied silently — they go back
 * to the model or require approval.
 */
data class Repair(
    val code: String,
    val step: String,
    val message: String,
    val kind: Kind,
    val candidate: String? = null,
    val confidence: Double = 1.0,
    val lossy: Boolean = false,
) {
    enum class Kind { REPLACE_TOOL, COERCE_ARG, FILL_DEFAULT, DROP_ARG }
}

/**
 * A problem with the plan. [repair] is present when the compiler found a
 * candidate fix (typically lossy, requiring model approval), null when the
 * problem is structural and unrepairable.
 */
data class Diagnostic(
    val code: String,
    val step: String,
    val message: String,
    val repair: Repair? = null,
)

/** A suggestion for LLM-driven repair, surfaced alongside [Invalid] results. */
data class RepairHint(
    val code: String,
    val step: String,
    val message: String,
    val suggestion: String,
)

sealed interface CompilationResult {
    data class Valid(
        val plan: ActionPlan,
    ) : CompilationResult

    data class Repaired(
        val originalPlan: ActionPlan,
        val repairedPlan: ActionPlan,
        val repairs: List<Repair>,
    ) : CompilationResult

    data class Invalid(
        val diagnostics: List<Diagnostic>,
        val repairHints: List<RepairHint> = emptyList(),
    ) : CompilationResult
}
