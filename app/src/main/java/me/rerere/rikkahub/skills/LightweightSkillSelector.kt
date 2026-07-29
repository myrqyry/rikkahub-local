package me.rerere.rikkahub.skills

import kotlin.math.ln
import kotlin.math.max

/**
 * A BM25-based skill selector that ranks [SkillCandidate] entries by textual
 * relevance to a free-form query. Pure Kotlin — no external dependencies.
 *
 * BM25 formula per query term:
 *   score(D, Q) = Σ IDF(q_i) · (f(q_i, D) · (k1 + 1)) / (f(q_i, D) + k1 · (1 - b + b · |D| / avgdl))
 *
 * Default parameters: k1 = 1.2, b = 0.75 (standard BM25 defaults).
 */
class LightweightSkillSelector(
    private val skills: List<SkillCandidate>,
    private val k1: Float = 1.2f,
    private val b: Float = 0.75f,
) {
    /**
     * Rank skills by relevance to [query]. Returns up to [topK] results that
     * score above [threshold].
     */
    fun select(query: String, topK: Int = 5, threshold: Float = 0.0f): List<ScoredSkill> {
        if (skills.isEmpty() || query.isBlank()) return emptyList()

        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return emptyList()

        val avgdl = avgDocLength()
        val docCount = skills.size
        val termDocFreq = computeDocFrequencies(queryTerms)

        val scored = skills.map { skill ->
            val docText = buildDocumentText(skill)
            val docLength = docText.length.toFloat()
            var score = 0.0f

            for (term in queryTerms) {
                val tf = termFrequency(term, docText).toFloat()
                val df = termDocFreq[term] ?: 0
                if (df == 0) continue

                val idf = ln((docCount - df + 0.5f) / (df + 0.5f) + 1.0f)
                val numerator = tf * (k1 + 1.0f)
                val denominator = tf + k1 * (1.0f - b + b * docLength / avgdl)
                score += idf * (numerator / denominator)
            }

            ScoredSkill(skill.id, score)
        }

        return scored
            .filter { it.score >= threshold }
            .sortedByDescending { it.score }
            .take(topK)
    }

    /**
     * Tokenize [text] by splitting on whitespace and punctuation, lowercasing
     * each token, and filtering out empty strings.
     */
    private fun tokenize(text: String): List<String> =
        text.split(Regex("""[\\s\\p{Punct}]+"""))
            .map { it.lowercase() }
            .filter { it.isNotBlank() }

    /**
     * Build a single searchable document from a [SkillCandidate]'s fields,
     * weighting the name and keywords more heavily by repeating them.
     */
    private fun buildDocumentText(skill: SkillCandidate): String {
        val parts = mutableListOf(
            skill.name,
            skill.description,
            skill.keywords.joinToString(" "),
        )
        // Repeat name and keywords for higher weight
        repeat(3) { parts.add(skill.name) }
        repeat(2) { parts.add(skill.keywords.joinToString(" ")) }
        return parts.joinToString(" ")
    }

    /**
     * Term frequency of [term] in [text] — simple count of occurrences.
     */
    private fun termFrequency(term: String, text: String): Int {
        val lower = text.lowercase()
        var count = 0
        var start = 0
        while (true) {
            val idx = lower.indexOf(term, start)
            if (idx < 0) break
            count++
            start = idx + term.length
        }
        return count
    }

    /**
     * Compute document frequency for each term in [queryTerms] across the
     * skill corpus. Returns a map of term → doc frequency.
     */
    private fun computeDocFrequencies(terms: List<String>): Map<String, Int> {
        val freq = mutableMapOf<String, Int>()
        val uniqueTerms = terms.distinct()
        for (term in uniqueTerms) {
            var count = 0
            for (skill in skills) {
                val doc = buildDocumentText(skill)
                if (doc.lowercase().contains(term)) {
                    count++
                }
            }
            freq[term] = count
        }
        return freq
    }

    /**
     * Average document length across all skills in the corpus.
     */
    private fun avgDocLength(): Float {
        if (skills.isEmpty()) return 1.0f
        val total = skills.sumOf { buildDocumentText(it).length }
        return max(total.toFloat() / skills.size, 1.0f)
    }
}

/**
 * A candidate skill for BM25-based selection.
 *
 * @property id Unique identifier for the skill.
 * @property name Display name of the skill.
 * @property description Short description of what the skill does.
 * @property keywords Additional search keywords for improved matching.
 */
data class SkillCandidate(
    val id: String,
    val name: String,
    val description: String,
    val keywords: List<String> = emptyList(),
)

/**
 * A skill that has been scored against a query.
 *
 * @property id The skill's unique identifier.
 * @property score BM25 relevance score (higher = more relevant).
 */
data class ScoredSkill(
    val id: String,
    val score: Float,
)