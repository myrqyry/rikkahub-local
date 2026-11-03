package me.rerere.rikkahub.data.ai.prompts

import me.rerere.rikkahub.data.datastore.Settings

/** Global utility prompts, deliberately separate from per-Facet instructions. */
enum class ChatUtilityPrompt {
    TRANSLATION, TITLE, SUGGESTION, OCR, COMPRESSION,
}

fun Settings.withUtilityPrompt(kind: ChatUtilityPrompt, value: String): Settings = when (kind) {
    ChatUtilityPrompt.TRANSLATION -> copy(translatePrompt = value)
    ChatUtilityPrompt.TITLE -> copy(titlePrompt = value)
    ChatUtilityPrompt.SUGGESTION -> copy(suggestionPrompt = value)
    ChatUtilityPrompt.OCR -> copy(ocrPrompt = value)
    ChatUtilityPrompt.COMPRESSION -> copy(compressPrompt = value)
}
