package me.rerere.reranker

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Reuses compiled Qwen semantic engines across callers.
 *
 * Search services and RAG previously compiled a fresh [QwenEmbedder]/[QwenReranker] per
 * request and disposed it immediately, throwing away the warm-up and session state.
 * Engines are expensive to build, so they are cached per model directory here.
 *
 * Callers that still hold a `cached-null` reference can re-query: when no engine exists
 * for a directory yet, a fresh one is built and installed under `putIfAbsent` so
 * concurrent callers share the winner. A failed build returns null without caching, so a
 * later install attempt can succeed.
 */
object QwenEngineRegistry {
    private val embedders = ConcurrentHashMap<String, QwenEmbedder>()
    private val rerankers = ConcurrentHashMap<String, QwenReranker>()

    fun embedder(modelDir: File): QwenEmbedder? {
        val key = modelDir.absolutePath
        embedders[key]?.let { return it }
        val candidate = runCatching { QwenEmbedder(modelDir) }.getOrNull() ?: return null
        val winner = embedders.putIfAbsent(key, candidate) ?: candidate
        if (winner !== candidate) candidate.close()
        return winner
    }

    fun reranker(modelDir: File): QwenReranker? {
        val key = modelDir.absolutePath
        rerankers[key]?.let { return it }
        val candidate = runCatching { QwenReranker(modelDir) }.getOrNull() ?: return null
        val winner = rerankers.putIfAbsent(key, candidate) ?: candidate
        if (winner !== candidate) candidate.close()
        return winner
    }

    fun invalidate(modelDir: File) {
        val key = modelDir.absolutePath
        embedders.remove(key)?.close()
        rerankers.remove(key)?.close()
    }
}
