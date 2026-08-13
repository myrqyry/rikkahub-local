package me.rerere.locallm.litert

import kotlinx.serialization.Serializable

/**
 * Phase 8 — one alternate plan offered to the evaluator.
 *
 * The primary model output is always present as a candidate with source
 * [ShadowCandidate.Source.PRIMARY]; any other candidate (an LLM-repair variant,
 * an alias-normalized rewrite, a procedure-split alternative, …) is supplied by
 * the caller. The evaluator scores every candidate deterministically WITHOUT
 * executing tools; the winner is chosen by ranking, never by side effect.
 */
data class ShadowCandidate(
    /** Stable id used for deterministic tie-breaking. */
    val id: String,
    val plan: ActionPlan,
    val source: Source,
) {
    enum class Source {
        PRIMARY,
        ALIAS_NORMALIZED,
        DEFAULT_FILLED,
        COERCED_ARGS,
        LLM_REPAIR,
    }
}

/**
 * Phase 8 — immutable, serialisable score of one candidate.
 *
 * Field-for-field plain data (no lambdas, no live object references) so scores can
 * cross module boundaries and be persisted by a future sink, mirroring the
 * [WorkflowReceipt] audit pattern.
 */
@Serializable
data class CandidateScore(
    val candidateId: String,
    val source: String,
    /** "valid" | "repaired" | "invalid" — the [CompilationResult] branch taken. */
    val compileOutcome: String,
    /** Human-readable repairs, present when [compileOutcome] == "repaired". */
    val repairs: List<String> = emptyList(),
    /** Composite deterministic score in [0.0, 1.0]; higher is better. */
    val score: Double,
    /** Fraction of requested capabilities that are granted, in [0.0, 1.0]. */
    val capabilityCoverage: Double,
    /** Human-readable reasons contributing to the score. */
    val reasons: List<String> = emptyList(),
)

/** Ranking result: [ranked] best-first, [winner] is `ranked.firstOrNull()`. */
data class EvaluationOutcome(
    val ranked: List<CandidateScore>,
    val winner: CandidateScore?,
)
