package me.rerere.rikkahub.data.agentrun

/** Central redaction and size boundary for trace metadata. */
class TracePayloadSanitizer {
    fun sanitize(summary: String?, payloadJson: String?): SanitizedTracePayload {
        return SanitizedTracePayload(
            summary = summary?.replace(SECRET_VALUE, "[REDACTED]")?.take(MAX_SUMMARY_CHARS),
            payloadJson = payloadJson
                ?.replace(SECRET_FIELD, "\$1\"[REDACTED]\"")
                ?.take(MAX_PAYLOAD_CHARS),
        )
    }

    data class SanitizedTracePayload(
        val summary: String?,
        val payloadJson: String?,
    )

    private companion object {
        const val MAX_SUMMARY_CHARS = 2_000
        const val MAX_PAYLOAD_CHARS = 48 * 1024
        val SECRET_VALUE = Regex("(?i)(bearer\\s+|api[_-]?key\\s*[:=]\\s*|authorization\\s*[:=]\\s*)[^\\s,}]+")
        val SECRET_FIELD = Regex("(?i)(\\\"(?:password|secret|token|api[_-]?key|authorization)\\\"\\s*:\\s*)\\\"[^\\\"]*\\\"")
    }
}
