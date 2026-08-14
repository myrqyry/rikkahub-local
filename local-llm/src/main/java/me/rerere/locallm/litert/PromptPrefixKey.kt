package me.rerere.locallm.litert

import java.security.MessageDigest
import kotlinx.serialization.Serializable

/**
 * Strict identity for reusing a warm Conversation's KV cache (roadmap K4).
 *
 * A warm KV prefix is reusable only when the requested identity matches the retained
 * identity EXACTLY: same model (id + revision), same tokenizer and runtime revisions,
 * same conversation + branch, and the same content-derived [prefixHash] of the prompt
 * turns the cache already consumed. Any mismatch — a model/tokenizer/runtime update, a
 * different conversation or branch, an edited/reordered/regenerated turn — must replace
 * the retained prefix, never silently reuse a stale cache.
 *
 * This is a SEPARATE invariant from [me.rerere.rikkahub.data.ai.revision.ConversationRevisionGuard]:
 * `prefixHash` answers "may I reuse KV state?", while the revision guard answers "may I
 * attach this completed work here?". Confusing the two is exactly the bug K4 prevents.
 *
 * The LiteRT SDK does not surface a per-call tokenizer counter, so [cachedPromptTokenCount]
 * is a best-effort estimate the caller supplies when known (null = unknown).
 */
@Serializable
data class PromptPrefixKey(
    val modelId: String,
    val modelRevision: String,
    val tokenizerRevision: String,
    val runtimeRevision: String?,
    val conversationId: String,
    val branchId: String,
    val prefixHash: String,
    val cachedPromptTokenCount: Long? = null,
) {
    /** Reuse is safe only when the retained key matches exactly (all identity fields AND the
     *  content-derived [prefixHash]). Any mismatch must go cold. */
    fun canReuse(retained: PromptPrefixKey?): Boolean = retained != null && retained == this

    companion object {
        private const val SEP = '\u0000'

        /**
         * Deterministic content hash of an ordered turn list, reusing [turnSignature] (the
         * same per-turn signature LiteRtRuntime uses for warm-continuation). SHA-256 hex
         * over the signatures joined with a NUL separator, so any reordered or edited turn
         * changes the hash and invalidates reuse.
         */
        fun prefixHash(turns: List<Turn>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            for (turn in turns) {
                digest.update(turnSignature(turn.role, turn.rawText).toByteArray(Charsets.UTF_8))
                digest.update(SEP.code.toByte())
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        /** Build the strict KV-reuse identity for a conversation whose prompt is [turns] (the
         *  turns the cache already consumed). [cachedPromptTokenCount] is a caller-supplied
         *  estimate of the prompt tokens held in cache (null = unknown). */
        fun of(
            modelId: String,
            modelRevision: String,
            tokenizerRevision: String,
            runtimeRevision: String?,
            conversationId: String,
            branchId: String,
            turns: List<Turn>,
            cachedPromptTokenCount: Long? = null,
        ): PromptPrefixKey = PromptPrefixKey(
            modelId = modelId,
            modelRevision = modelRevision,
            tokenizerRevision = tokenizerRevision,
            runtimeRevision = runtimeRevision,
            conversationId = conversationId,
            branchId = branchId,
            prefixHash = prefixHash(turns),
            cachedPromptTokenCount = cachedPromptTokenCount,
        )
    }
}
