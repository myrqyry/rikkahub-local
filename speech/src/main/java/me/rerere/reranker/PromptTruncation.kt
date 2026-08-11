package me.rerere.reranker

/**
 * Truncates a structured prompt to [maxTokens] by trimming only the variable parts.
 *
 * The structural [suffix] is always preserved. Space is first taken from the
 * [document]; only in the pathological case where even the [prefix] alone exceeds
 * the budget is the tail of the prefix (query/instruction text) clipped, so the
 * system block and structural tokens survive.
 */
internal fun buildTruncatedPrompt(
    prefix: IntArray,
    document: IntArray,
    suffix: IntArray,
    maxTokens: Int,
): IntArray {
    val prefixBudget = (maxTokens - suffix.size).coerceAtLeast(0)
    val keptPrefix = if (prefix.size <= prefixBudget) prefix else prefix.copyOf(prefixBudget)
    val documentLength = (maxTokens - keptPrefix.size - suffix.size)
        .coerceIn(0, document.size)
    return IntArray(keptPrefix.size + documentLength + suffix.size).also { result ->
        keptPrefix.copyInto(result)
        document.copyInto(result, destinationOffset = keptPrefix.size, endIndex = documentLength)
        suffix.copyInto(result, destinationOffset = keptPrefix.size + documentLength)
    }
}
