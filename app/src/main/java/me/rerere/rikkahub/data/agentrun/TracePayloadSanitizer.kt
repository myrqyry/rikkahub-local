package me.rerere.rikkahub.data.agentrun

data class SanitizedTracePayload(
    val summary: String?,
    val payloadJson: String?,
)

/**
 * Central redaction point for trace content. Never persist auth headers, keys, creds, or
 * unbounded payloads. Applies to both the short summary and the JSON payload before the
 * event is written.
 */
class TracePayloadSanitizer {

    fun sanitize(summary: String?, payloadJson: String?): SanitizedTracePayload {
        return SanitizedTracePayload(
            summary = summary?.replace(SECRET_VALUE, "[REDACTED]")?.take(MAX_SUMMARY_CHARS),
            payloadJson = payloadJson?.replace(SECRET_FIELD, "$1\"[REDACTED]\"")?.take(MAX_PAYLOAD_CHARS),
        )
    }

    companion object {
        const val MAX_SUMMARY_CHARS = 2_000
        const val MAX_PAYLOAD_CHARS = 48 * 1024

        private val SECRET_VALUE = Regex(
            "(?i)(bearer\\s+|api[_-]?key\\s*[:=]\\s*|authorization\\s*[:=]\\s*)[^,}]+"
        )

        private val SECRET_FIELD = Regex(
            "(?i)(\"(?:password|secret|token|api[_-]?key|authorization)\"\\s*:\\s*)\"[^\"]*\""
        )
    }
}
