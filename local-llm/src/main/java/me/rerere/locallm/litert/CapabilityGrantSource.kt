package me.rerere.locallm.litert

/**
 * Supplies the set of capabilities the user has explicitly granted for this request.
 *
 * The [LiteRtToolBridge] consults this before building a [CapabilityGrant] so that only
 * user-approved tools are exercisable — the grant no longer silently includes every
 * registered tool. When null (unit tests, or an SDK embedder that has not wired a source)
 * the bridge falls back to granting the full registered set for backward compatibility.
 */
fun interface CapabilityGrantSource {
    /**
     * Return the subset of [registered] tool names the user has explicitly granted.
     *
     * @param registered the tools registered for the current request
     */
    suspend fun grantedToolNames(registered: Set<String>): Set<String>
}
