package me.rerere.locallm

data class ModelEntry(
    val id: String,
    val displayName: String,
    val runtime: LocalRuntime,
    val family: String? = null,
    val format: String,
    val source: ModelSource,
    val sourceUrl: String? = null,
    val filePath: String,
    val sizeBytes: Long,
    val license: String? = null,
    val validated: Boolean,
    val addedAt: Long,
)

sealed interface ModelSource {
    data class Catalog(val entryId: String) : ModelSource
    data class CustomUrl(val url: String) : ModelSource
    data object LocalImport : ModelSource
}
