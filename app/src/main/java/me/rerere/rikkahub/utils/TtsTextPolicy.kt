package me.rerere.rikkahub.utils

import me.rerere.rikkahub.data.datastore.TtsPlaybackPreferences

/**
 * One policy for manual playback and post-generation auto-play.
 * A restrictive option never silently falls back to reading excluded text.
 * Null means no permitted speech content.
 */
fun prepareTtsText(text: String, settings: TtsPlaybackPreferences): String? {
    var allowed = text
    if (settings.quotedOnly) {
        allowed = allowed.extractQuotedContentAsText() ?: return null
    }
    if (settings.skipBracketed) {
        allowed = allowed.removeBracketedContent() ?: return null
    }
    return allowed.trim().takeIf { it.isNotBlank() }
}
