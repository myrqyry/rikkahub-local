package me.rerere.rikkahub.data.rag

data class Chunk(
    val id: String,
    val text: String,
    val headingPath: List<String>,
    val position: Int,
)

class MarkdownDocumentChunker(
    private val maxChunkSize: Int = 512,
    private val overlap: Int = 64,
) {
    private val headingRegex = Regex("^(#{1,6})\\s+(.+)$", RegexOption.MULTILINE)

    fun chunk(document: String, docId: String): List<Chunk> {
        val sections = splitByHeadings(document)
        val chunks = mutableListOf<Chunk>()
        var position = 0

        for (section in sections) {
            val headingPath = section.headingPath
            val content = section.content.trim()

            if (content.isEmpty()) continue

            val headingPrefix = if (headingPath.isNotEmpty()) {
                headingPath.joinToString(" > ") + "\n\n"
            } else {
                ""
            }

            val paragraphs = splitIntoParagraphs(content)
            var currentChunk = StringBuilder()

            for (paragraph in paragraphs) {
                val candidateText = headingPrefix + currentChunk.toString().trimEnd()
                val paragraphWithPrefix = if (currentChunk.isEmpty()) {
                    headingPrefix + paragraph
                } else {
                    "\n\n" + paragraph
                }

                val estimatedTokens = (candidateText.length + paragraphWithPrefix.length) / 4

                if (estimatedTokens > maxChunkSize && currentChunk.isNotEmpty()) {
                    chunks.add(
                        Chunk(
                            id = "${docId}_$position",
                            text = (headingPrefix + currentChunk.toString().trim()),
                            headingPath = headingPath,
                            position = position,
                        )
                    )
                    position++
                    currentChunk = StringBuilder()
                    val overlapText = extractOverlap(chunks.lastOrNull())
                    if (overlapText != null) {
                        currentChunk.append(overlapText)
                        currentChunk.append("\n\n")
                    }
                    currentChunk.append(paragraph)
                } else {
                    if (currentChunk.isNotEmpty()) {
                        currentChunk.append("\n\n")
                    }
                    currentChunk.append(paragraph)
                }
            }

            val remaining = currentChunk.toString().trim()
            if (remaining.isNotEmpty()) {
                val text = if (position == 0 || !remaining.startsWith(headingPrefix)) {
                    headingPrefix + remaining
                } else {
                    remaining
                }
                chunks.add(
                    Chunk(
                        id = "${docId}_$position",
                        text = text,
                        headingPath = headingPath,
                        position = position,
                    )
                )
                position++
            }
        }

        return chunks
    }

    private fun splitByHeadings(document: String): List<Section> {
        val matches = headingRegex.findAll(document).toList()
        if (matches.isEmpty()) {
            return listOf(Section(emptyList(), document.trim()))
        }

        val sections = mutableListOf<Section>()
        val headingStack = mutableListOf<Pair<Int, String>>()

        for (i in matches.indices) {
            val match = matches[i]
            val level = match.groupValues[1].length
            val headingText = match.groupValues[2].trim()

            while (headingStack.isNotEmpty() && headingStack.last().first >= level) {
                headingStack.removeLast()
            }
            headingStack.add(level to headingText)

            val start = match.range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else document.length
            val content = document.substring(start, end).trim()

            val headingPath = headingStack.map { it.second }
            sections.add(Section(headingPath, content))
        }

        val firstStart = 0
        val firstEnd = if (matches.isNotEmpty()) matches.first().range.first else document.length
        val preamble = document.substring(firstStart, firstEnd).trim()
        if (preamble.isNotEmpty()) {
            sections.add(0, Section(emptyList(), preamble))
        }

        return sections
    }

    private fun splitIntoParagraphs(text: String): List<String> {
        return text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun extractOverlap(previousChunk: Chunk?): String? {
        if (previousChunk == null) return null
        val words = previousChunk.text.split(Regex("\\s+"))
        val overlapTokens = (overlap * 4).coerceAtMost(words.size)
        if (overlapTokens <= 0) return null
        return words.takeLast(overlapTokens).joinToString(" ")
    }

    private data class Section(
        val headingPath: List<String>,
        val content: String,
    )
}