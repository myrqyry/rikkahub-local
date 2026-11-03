package me.rerere.rikkahub.ui.pages.chat

import kotlin.uuid.Uuid
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.datastore.TtsPlaybackPreferences
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.prepareTtsText

/** Only a new assistant response in the completed conversation may auto-play. */
internal data class TtsAutoPlayCandidate(val messageId: Uuid, val text: String)

internal fun eligibleTtsAutoPlay(
    completedId: Uuid,
    current: Conversation,
    options: TtsPlaybackPreferences,
    lastPlayedMessageId: Uuid?,
): TtsAutoPlayCandidate? {
    if (!options.autoPlay || current.id != completedId) return null
    val last = current.currentMessages.lastOrNull() ?: return null
    if (last.role != MessageRole.ASSISTANT || last.id == lastPlayedMessageId) return null
    val text = prepareTtsText(last.toText(), options) ?: return null
    return TtsAutoPlayCandidate(last.id, text)
}
