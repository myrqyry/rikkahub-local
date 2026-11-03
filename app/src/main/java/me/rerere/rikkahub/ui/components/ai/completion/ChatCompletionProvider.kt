package me.rerere.rikkahub.ui.components.ai.completion

import androidx.annotation.DrawableRes
import androidx.compose.ui.text.TextRange

data class ChatCompletionContext(
    val text: String,
    val selection: TextRange,
) {
    val cursor: Int = selection.max
    val hasSelection: Boolean = selection.min != selection.max
}

data class ChatCompletionList(
    val providerId: String,
    val replacementRange: TextRange,
    val items: List<ChatCompletionItem>,
)

data class ChatCompletionItem(
    val label: String,
    val insertText: String,
    val detail: String? = null,
    @DrawableRes val icon: Int? = null,
    val sortScore: Int = 0,
)

interface ChatCompletionProvider {
    val id: String

    suspend fun complete(context: ChatCompletionContext): ChatCompletionList?
}
