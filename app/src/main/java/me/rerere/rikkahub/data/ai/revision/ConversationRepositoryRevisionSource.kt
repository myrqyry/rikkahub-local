package me.rerere.rikkahub.data.ai.revision

import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.repository.ConversationRepository

/**
 * Bridges the current conversation store to the revision guard.
 *
 * Branching is not persisted yet, so the conversation identity is used as the stable
 * branch identity. A future branch table can replace this adapter without changing the
 * guard API.
 */
class ConversationRepositoryRevisionSource(
    private val repository: ConversationRepository,
) : ConversationRevisionSource {
    override suspend fun currentState(conversationId: String): ConversationSnapshot? {
        val id = runCatching { Uuid.parse(conversationId) }.getOrNull() ?: return null
        val conversation = repository.getConversationById(id) ?: return null
        return ConversationSnapshot(
            conversationId = conversation.id.toString(),
            branchId = conversation.id.toString(),
            revision = conversation.revision,
        )
    }
}
