package me.rerere.locallm.litert.zero

/**
 * Phase 9 — storage seam for mined/cached [ZeroProcedure]s.
 *
 * The miner itself is pure and never persists; a [ProcedureCache] implementation
 * (wired via DI in `app`, e.g. a DataStore or Room-backed store) is what makes a
 * mined procedure reusable across sessions. The interface is nullable-safe like the
 * other local-llm seams: a missing cache only loses the reuse benefit, never the
 * executor's core contract.
 */
interface ProcedureCache {
    /** Upsert [procedure] keyed by [ZeroProcedure.id]. */
    suspend fun put(procedure: ZeroProcedure)

    /** The stored procedure with [id], or null when absent. */
    suspend fun get(id: String): ZeroProcedure?

    /** All stored procedures, in insertion order. */
    suspend fun all(): List<ZeroProcedure>
}
