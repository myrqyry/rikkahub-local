package me.rerere.rikkahub.data.ai.compressor

import kotlin.random.Random
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

data class CompressionResult(
    val compressed: String,
    val contentKey: String?,
    val originalSize: Int,
    val compressedSize: Int,
)

object ContentCompressor {
    const val THRESHOLD = 8000
    const val MAX_PREVIEW = 6000

    fun compress(key: String, text: String, store: CompressedContentStore): CompressionResult {
        if (text.length <= THRESHOLD) return CompressionResult(text, null, text.length, text.length)
        store.store(key, text)
        val compressed = compressText(text, key)
        return CompressionResult(compressed, key, text.length, compressed.length)
    }

    fun wrapTool(tool: Tool, store: CompressedContentStore): Tool {
        val originalExecute = tool.execute
        return Tool(
            name = tool.name,
            description = tool.description,
            parameters = tool.parameters,
            needsApproval = tool.needsApproval,
            execute = { args ->
                originalExecute(args).map { part ->
                    if (part is UIMessagePart.Text && part.text.length > THRESHOLD) {
                        val key = "tool_${tool.name}_${Random.nextLong()}"
                        val result = compress(key, part.text, store)
                        if (result.contentKey != null) UIMessagePart.Text(result.compressed) else part
                    } else part
                }
            },
        )
    }

    private fun compressText(text: String, key: String): String {
        val trimmed = text.trimStart()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val minified = text.filterNot { it == ' ' || it == '\n' || it == '\r' || it == '\t' }
            if (minified.length <= THRESHOLD) return minified
            val header = minified.take(2000)
            val footer = minified.takeLast(2000)
            return "$header\n\n[... ${minified.length - 4000} chars compressed, retrieve_content key=$key]\n\n$footer"
        }
        val lines = text.lines()
        if (lines.size <= 60) {
            if (text.length <= THRESHOLD + 2000) return text
            return text.take(MAX_PREVIEW) + "\n\n[... ${text.length - MAX_PREVIEW} more chars, retrieve_content key=$key]"
        }
        val head = lines.take(20).joinToString("\n")
        val tail = lines.takeLast(20).joinToString("\n")
        return "$head\n\n[... ${lines.size - 40} lines compressed, retrieve_content key=$key]\n\n$tail"
    }
}
