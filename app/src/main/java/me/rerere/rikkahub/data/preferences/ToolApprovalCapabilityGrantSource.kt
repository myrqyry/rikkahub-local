package me.rerere.rikkahub.data.preferences

import me.rerere.locallm.litert.CapabilityGrantSource

/**
 * Production [CapabilityGrantSource]: the user's explicit tool grants from
 * [ToolApprovalPreferences] — the "Always Allow" allow-list, or every registered tool when
 * the "I AM STUPID" global auto-approve flag is on. Per-chat grants in
 * [me.rerere.rikkahub.data.ai.tools.ToolApprovalAllowList] are conversation-scoped and not
 * surfaced here (the LiteRT tool bridge has no conversation identity).
 */
class ToolApprovalCapabilityGrantSource(
    private val prefs: ToolApprovalPreferences,
) : CapabilityGrantSource {
    override suspend fun grantedToolNames(registered: Set<String>): Set<String> {
        if (prefs.currentYolo()) return registered
        return registered.intersect(prefs.current())
    }
}
