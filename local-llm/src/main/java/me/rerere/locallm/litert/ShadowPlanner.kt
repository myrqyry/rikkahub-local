package me.rerere.locallm.litert

/**
 * Phase C2 — operational selection: propose [CandidatePlanner] variants, run every
 * candidate through [ShadowCandidateEvaluator] (no execution, deterministic), and
 * choose the winner. The winning *plan* is returned alongside its score so a caller
 * can dispatch exactly what was ranked.
 *
 * Selection rule (roadmap C2): an invalid candidate never wins unless *every*
 * candidate is invalid, in which case selection fails loudly and nothing executes.
 * When the winner is a repaired plan (e.g. alias-normalized, default-filled,
 * coerced), the repaired plan is dispatched — never the raw input.
 */
class ShadowPlanner(
    private val planner: CandidatePlanner = CandidatePlanner(),
    private val evaluator: ShadowCandidateEvaluator = ShadowCandidateEvaluator(),
) {

    /** Result of selecting the best candidate for a primary plan. */
    sealed interface Selection {
        /**
         * [plan] is the winning plan to dispatch; [score] is its ranked score;
         * [ranked] is the full deterministic ranking (best-first) of every candidate
         * so a caller can persist the whole evaluation as trace events (roadmap C3).
         */
        data class Selected(
            val plan: ActionPlan,
            val score: CandidateScore,
            val ranked: List<CandidateScore> = listOf(score),
        ) : Selection

        /**
         * Every candidate was invalid; [diagnostics] explain why. Nothing executes.
         * [ranked] carries the scored candidates so even the rejected attempt is
         * auditable (roadmap C3).
         */
        data class AllInvalid(val diagnostics: List<String>, val ranked: List<CandidateScore> = emptyList()) : Selection
    }

    /**
     * Select the best candidate for [plan]. Always proposes at least the primary;
     * evaluates all proposed candidates deterministically; returns the winner with
     * the full ranked outcome attached so callers can persist every score.
     */
    suspend fun select(plan: ActionPlan, context: CompilationContext): Selection {
        val candidates = planner.propose(plan, context)
        val outcome = evaluator.evaluate(candidates, context)
        val winner = outcome.winner ?: return Selection.AllInvalid(emptyList(), outcome.ranked)
        if (winner.compileOutcome == "invalid") {
            return Selection.AllInvalid(winner.reasons, outcome.ranked)
        }
        val winningPlan = candidates.firstOrNull { it.id == winner.candidateId }?.plan ?: plan
        return Selection.Selected(winningPlan, winner, outcome.ranked)
    }
}
