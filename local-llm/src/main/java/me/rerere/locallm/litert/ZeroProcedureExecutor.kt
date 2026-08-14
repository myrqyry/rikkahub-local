package me.rerere.locallm.litert

/**
 * Canonical roadmap B5 — seam for executing a stored Zero procedure by id.
 *
 * Lives in `local-llm` (like [ZeroWorkflowExecutor]) because the deterministic substrate is
 * here, but a real implementation needs a persisted procedure repository (the
 * [me.rerere.locallm.litert.zero.ProcedureCache] seam) and a tool catalog, both of which are
 * wired app-side via DI. A null implementation is safe: a ProcedureCall simply fails loudly
 * instead of being dropped.
 */
fun interface ZeroProcedureExecutor {
    suspend fun execute(plan: ActionPlan.ProcedureCall): ActionPlanResult
}
