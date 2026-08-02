package me.rerere.locallm

data class SdCatalogEntry(
    val displayName: String,
    val family: String,
    val format: String,
    val description: String,
    val modelId: String,
    val modelFile: String,
    val sizeBytes: Long,
    val license: String,
    val minDeviceMemoryGb: Int,
    val recommended: Boolean = false,
    val tags: List<String> = emptyList(),
) {
    fun resolveUrl(): String = "https://huggingface.co/$modelId/resolve/main/$modelFile"
}

object SdCatalog {
    val ENTRIES: List<SdCatalogEntry> = listOf(
        // Empirically verified entries only — add as tested on Pixel 9a / Pixel 10 Pro
    )

    fun findById(modelId: String): SdCatalogEntry? =
        ENTRIES.firstOrNull { it.modelId == modelId }

    fun findByFamily(family: String): List<SdCatalogEntry> =
        ENTRIES.filter { it.family == family }
}
