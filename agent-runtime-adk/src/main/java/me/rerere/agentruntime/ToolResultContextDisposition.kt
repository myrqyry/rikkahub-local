package me.rerere.agentruntime

enum class ToolResultDisposition {
    INLINE,
    REDUCE,
    INDEX_EXACT,
    REFERENCE_ONLY,
    DISCARD,
}

data class ToolResultContextCandidate(
    val toolName: String,
    val contentType: String,
    val byteSize: Int,
    val structured: Boolean,
    val evidenceId: String?,
    val exactRetrievable: Boolean,
)

fun interface ToolResultContextPolicy {
    fun decide(candidate: ToolResultContextCandidate): ToolResultDisposition
}

class DeterministicToolResultContextPolicy(
    private val maxInlineBytes: Int,
) : ToolResultContextPolicy {
    override fun decide(candidate: ToolResultContextCandidate): ToolResultDisposition =
        if (candidate.byteSize <= maxInlineBytes) ToolResultDisposition.INLINE
        else ToolResultDisposition.REDUCE
}

data class ToolResultContextProjection(
    val disposition: ToolResultDisposition,
    val text: String?,
)

object ToolResultContextProcessor {
    fun effectiveDisposition(
        requested: ToolResultDisposition,
        candidate: ToolResultContextCandidate,
    ): ToolResultDisposition = when {
        requested == ToolResultDisposition.INDEX_EXACT && !candidate.exactRetrievable ->
            ToolResultDisposition.REFERENCE_ONLY
        requested == ToolResultDisposition.REFERENCE_ONLY && candidate.evidenceId == null ->
            ToolResultDisposition.REDUCE
        else -> requested
    }

    fun project(
        requested: ToolResultDisposition,
        text: String,
        candidate: ToolResultContextCandidate,
        maxInlineBytes: Int,
    ): ToolResultContextProjection {
        val disposition = effectiveDisposition(requested, candidate)
        val projectedText = when (disposition) {
            ToolResultDisposition.INLINE -> text
            ToolResultDisposition.REDUCE -> ContextDisposition.capBytes(text, maxInlineBytes)
            ToolResultDisposition.INDEX_EXACT,
            ToolResultDisposition.REFERENCE_ONLY,
                -> candidate.evidenceId?.let { evidenceId ->
                    ContextDisposition.capBytes("[Tool result preserved as evidence: $evidenceId]", maxInlineBytes)
                } ?: ContextDisposition.capBytes(text, maxInlineBytes)
            ToolResultDisposition.DISCARD -> null
        }
        return ToolResultContextProjection(disposition, projectedText)
    }
}
