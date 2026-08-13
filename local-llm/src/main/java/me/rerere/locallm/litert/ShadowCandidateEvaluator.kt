package me.rerere.locallm.litert

/**
 * Phase 8 — deterministic evaluator for [ShadowCandidate]s.
 *
 * Evaluates WITHOUT executing any tool: every candidate is run through the
 * [ActionPlanCompiler] compile gate (pure, no side effects), then scored on:
 *
 *  - **compile outcome** — valid &gt; repaired &gt; invalid
 *  - **repair cost** — fewer repairs score higher; lossy repairs are penalised
 *    harder than lossless ones (a lossy repair still needs model approval or
 *    user consent, so a plan carrying one is strictly riskier)
 *  - **capability coverage** — the fraction of requested capabilities that the
 *    plan's grant actually covers
 *
 * `score = compileScore * capabilityCoverage`, with an invalid plan always
 * scoring 0. Ties break on candidate id, so the ranking is fully deterministic.
 * The winner is the top of the ranked list; nothing here ever invokes a tool.
 */
class ShadowCandidateEvaluator(
    private val compiler: ActionPlanCompiler = DefaultActionPlanCompiler(),
) {

    suspend fun evaluate(
        candidates: List<ShadowCandidate>,
        context: CompilationContext,
    ): EvaluationOutcome {
        val ranked = candidates
            .map { score(it, context) }
            .sortedWith(compareByDescending<CandidateScore> { it.score }.thenBy { it.candidateId })
        return EvaluationOutcome(ranked, ranked.firstOrNull())
    }

    private suspend fun score(candidate: ShadowCandidate, context: CompilationContext): CandidateScore {
        val compiled = compiler.compile(candidate.plan, context)
        val (compileOutcome, repairs, compileReasons) = when (compiled) {
            is CompilationResult.Valid -> Triple("valid", emptyList<String>(), listOf("compiled_valid"))
            is CompilationResult.Repaired -> Triple(
                "repaired",
                compiled.repairs.map { "${it.code}(${it.step})" },
                compiled.repairs.map { "${it.code}(${it.step}): ${it.message}" },
            )
            is CompilationResult.Invalid -> Triple(
                "invalid",
                emptyList(),
                compiled.diagnostics.map { "${it.code}(${it.step}): ${it.message}" },
            )
        }
        val coverage = capabilityCoverage(candidate.plan)
        val reasons = compileReasons.toMutableList()
        if (coverage < 1.0) {
            reasons += "capability_coverage ${candidate.plan.grant.requestedCapabilities.count { it in candidate.plan.grant.grantedCapabilities }}/${candidate.plan.grant.requestedCapabilities.size}"
        }
        return CandidateScore(
            candidateId = candidate.id,
            source = candidate.source.name,
            compileOutcome = compileOutcome,
            repairs = repairs,
            score = compileScore(compiled) * coverage,
            capabilityCoverage = coverage,
            reasons = reasons,
        )
    }

    private fun compileScore(compiled: CompilationResult): Double = when (compiled) {
        is CompilationResult.Invalid -> 0.0
        is CompilationResult.Valid -> 1.0
        is CompilationResult.Repaired -> {
            val lossy = compiled.repairs.count { it.lossy }
            maxOf(0.1, 1.0 - 0.15 * compiled.repairs.size - 0.25 * lossy)
        }
    }

    private fun capabilityCoverage(plan: ActionPlan): Double {
        val requested = plan.grant.requestedCapabilities
        if (requested.isEmpty()) return 1.0
        val granted = requested.count { it in plan.grant.grantedCapabilities }
        return granted.toDouble() / requested.size
    }
}
