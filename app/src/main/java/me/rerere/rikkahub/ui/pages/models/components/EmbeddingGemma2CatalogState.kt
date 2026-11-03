package me.rerere.rikkahub.ui.pages.models.components

/**
 * Distinguishes the user's selection from a usable local file.
 *
 * Expected-size checks are cheap UI hints, not SHA-256 verification. Selecting
 * a variant still performs the real SHA-256 check before updating settings.
 */
internal enum class EmbeddingGemma2CatalogState {
    NOT_DOWNLOADED,
    INCOMPLETE,
    AVAILABLE,
    SELECTED_MISSING,
    SELECTED_INCOMPLETE,
    SELECTED_AVAILABLE,
}

internal fun embeddingGemma2CatalogState(
    selected: Boolean,
    fileExists: Boolean,
    expectedSize: Boolean,
): EmbeddingGemma2CatalogState = when {
    selected && !fileExists -> EmbeddingGemma2CatalogState.SELECTED_MISSING
    selected && !expectedSize -> EmbeddingGemma2CatalogState.SELECTED_INCOMPLETE
    selected -> EmbeddingGemma2CatalogState.SELECTED_AVAILABLE
    !fileExists -> EmbeddingGemma2CatalogState.NOT_DOWNLOADED
    !expectedSize -> EmbeddingGemma2CatalogState.INCOMPLETE
    else -> EmbeddingGemma2CatalogState.AVAILABLE
}
