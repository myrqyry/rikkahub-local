package me.rerere.locallm.litert.zero

/**
 * Explicit review lifecycle for a mined/synthesized procedure (roadmap B8).
 *
 * A mined procedure is NEVER automatically promoted to executable just because it
 * was observed more than once — humans repeat bad ideas with remarkable consistency.
 * The lifecycle is: mined/generated procedures land as [CANDIDATE]; an explicit
 * human or policy decision promotes to [APPROVED] (awaiting enable) then [ENABLED];
 * or demotes to [REJECTED]. [ENABLED] is the only state in which execution authority
 * exists.
 */
enum class ProcedureReviewState {
    CANDIDATE,
    APPROVED,
    ENABLED,
    REJECTED;

    /** Whether execution authority is granted. Only [ENABLED]. */
    val executable: Boolean get() = this == ENABLED
}
