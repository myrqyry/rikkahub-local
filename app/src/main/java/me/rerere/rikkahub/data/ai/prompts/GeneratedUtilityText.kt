package me.rerere.rikkahub.data.ai.prompts

import java.util.Locale

/** Output normalization remains a deterministic guard if the model ignores the prompt. */
internal fun generatedConversationTitle(raw: String?): String? =
    raw?.lineSequence()?.firstOrNull()?.trim()
        ?.trim('"', '\'', '`', '#', '*', ' ', '\t')
        ?.take(84)?.trim()?.takeIf { it.isNotBlank() }

internal fun generatedReplySuggestions(raw: String?): List<String> =
    raw.orEmpty().lineSequence()
        .map { it.trim().replace(Regex("^(?:[-*]\\s+|\\d+[.)]\\s+)"), "").trim() }
        .filter { it.isNotEmpty() && it.length <= 160 }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .take(5).toList()
