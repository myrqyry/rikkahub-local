package me.rerere.agentruntime

import java.nio.charset.StandardCharsets

/**
 * Context-disposition primitives harvested from Context Mode's `truncate.ts`.
 * Bounds what stays in the model window (what the runtime disposes of) with
 * byte-safe / char-safe truncation that never splits a UTF-8 sequence or a
 * surrogate pair, so truncated output round-trips without U+FFFD.
 */
object ContextDisposition {

    private const val TRUNCATED_MARKER = "... [truncated]"

    /**
     * Longest prefix of [str] whose UTF-8 encoding is at most [maxBytes].
     * Binary search (avoid O(n²) rescans). Never splits a lone high surrogate.
     */
    fun byteSafePrefix(str: String, maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        var lo = 0
        var hi = str.length
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            val candidate = if (mid == str.length) str else str.substring(0, mid)
            if (candidate.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) {
                lo = mid
            } else {
                hi = mid - 1
            }
        }
        return if (lo > 0 && lo < str.length) {
            val trimmed = str.substring(0, lo)
            if (trimmed.last().isHighSurrogate()) trimmed.dropLast(1) else trimmed
        } else if (lo == 0) {
            // Even one code unit overflows; give up on a lone surrogate.
            ""
        } else {
            str.substring(0, lo)
        }
    }

    /**
     * Prefix of [str] of at most [maxChars] UTF-16 code units, never ending on
     * a lone high surrogate (avoids invalid JSON when re-encoded).
     */
    fun charSafePrefix(str: String, maxChars: Int): String {
        if (maxChars <= 0) return ""
        val end = minOf(maxChars, str.length)
        return if (end > 0 && end < str.length && str[end - 1].isHighSurrogate()) {
            str.substring(0, end - 1)
        } else {
            str.substring(0, end)
        }
    }

    /**
     * Cap [str] to [maxBytes] (UTF-8) appending an ellipsis; result is always
     * at most [maxBytes] bytes. For single-value display fields.
     */
    fun capBytes(str: String, maxBytes: Int): String {
        if (str.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) return str
        val ellipsis = "..."
        val prefix = byteSafePrefix(str, (maxBytes - ellipsis.toByteArray(StandardCharsets.UTF_8).size).coerceAtLeast(0))
        return prefix + ellipsis
    }

    /**
     * JSON-encode [value] then truncate to [maxBytes] at a UTF-8-safe boundary
     * with a truncated marker. Result is display/logging-only, not valid JSON.
     */
    fun truncateJson(value: Any?, maxBytes: Int): String {
        // kotlinx.serialization is not a dependency of this module; mirror
        // JSON.stringify with the plain toString of the given value.
        val encoded = value?.toString() ?: "null"
        if (encoded.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) return encoded
        val marker = TRUNCATED_MARKER
        val budget = (maxBytes - marker.toByteArray(StandardCharsets.UTF_8).size).coerceAtLeast(0)
        return byteSafePrefix(encoded, budget) + marker
    }
}

/**
 * Continuation/context accounting harvested from Context Mode's
 * `retrieval-marker.ts`: the runtime records how many bytes a continuation
 * consumed, and the marker is consumed once so a later pass cannot re-forward
 * the same bytes. Append-only, positive-only, per-key.
 */
class ContinuationMarker {

    private val pending = HashMap<String, Long>()

    /** Record [bytes] for [key] if it is positive. */
    fun append(key: String, bytes: Long) {
        if (bytes <= 0) return
        pending[key] = (pending[key] ?: 0L) + bytes
    }

    /** Return the accumulated byte count for [key] and clear it. */
    fun consume(key: String): Long {
        val total = pending.remove(key) ?: 0L
        return total
    }
}
