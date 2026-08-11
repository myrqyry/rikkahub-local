package me.rerere.reranker

internal fun buildTruncatedPrompt(
    prefix: IntArray,
    document: IntArray,
    suffix: IntArray,
    maxTokens: Int,
): IntArray {
    require(prefix.size + suffix.size <= maxTokens) {
        "Prompt structure exceeds the model token limit"
    }
    val documentLength = (maxTokens - prefix.size - suffix.size)
        .coerceAtMost(document.size)
    return IntArray(prefix.size + documentLength + suffix.size).also { result ->
        prefix.copyInto(result)
        document.copyInto(result, destinationOffset = prefix.size, endIndex = documentLength)
        suffix.copyInto(result, destinationOffset = prefix.size + documentLength)
    }
}
