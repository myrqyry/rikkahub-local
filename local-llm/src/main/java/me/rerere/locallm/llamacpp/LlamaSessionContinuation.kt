package me.rerere.locallm.llamacpp

/**
 * Decides whether an incoming message history can continue an existing
 * llama.cpp session (same KV cache) or must start fresh.
 *
 * A session continues only when the stored history is a strict prefix of the
 * incoming history that grew by exactly one message — i.e. the assistant reply
 * to the previous turn plus the new user message. Anything else (rewind,
 * divergence, jump) requires resetting the session so the model does not
 * hallucinate from a stale KV cache.
 */
object LlamaSessionContinuation {

    /** True when [stored] is a strict prefix of [incoming] grown by one message. */
    fun shouldContinue(stored: List<String>, incoming: List<String>): Boolean {
        if (stored.isEmpty()) return false
        if (incoming.size != stored.size + 1) return false
        return incoming.take(stored.size) == stored
    }

    /** The new user message to stream into the continued session. */
    fun continuationText(incoming: List<String>): String? {
        if (incoming.size < 2) return null
        return incoming.last()
    }
}
