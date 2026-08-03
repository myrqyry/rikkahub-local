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

    /** Where the user obtains this model — shown in the catalog UI and opened via ACTION_VIEW. */
    val sourceUrl: String get() = "https://huggingface.co/$modelId"
}

object SdCatalog {
    /**
     * Curated on-device SD models in stable-diffusion.cpp GGUF format.
     *
     * URL + filename existence verified against the HuggingFace API for each repo.
     * All files carry the GGUF magic (0x47475546) which ModelInstall validates on
     * download, so a wrong-format file is rejected before it ever lands on disk.
     *
     * sizeBytes values are display-only approximations; minDeviceMemoryGb is the
     * documented device-RAM floor for a usable generation.
     */
    val ENTRIES: List<SdCatalogEntry> = listOf(
        SdCatalogEntry(
            displayName = "SD-Turbo 2.1 (Q8_0)",
            family = "sdturbo",
            format = "gguf",
            description = "Stable Diffusion Turbo 2.1, 1-step distilled. Best phone fit: fast, small, no guidance tuning needed.",
            modelId = "gpustack/stable-diffusion-v2-1-turbo-GGUF",
            modelFile = "stable-diffusion-v2-1-turbo-Q8_0.gguf",
            sizeBytes = 2_320_000_000,
            license = "other",
            minDeviceMemoryGb = 4,
            recommended = true,
            tags = listOf("fast", "distilled", "1-step"),
        ),
        SdCatalogEntry(
            displayName = "SD-Turbo 2.1 (Q4_0)",
            family = "sdturbo",
            format = "gguf",
            description = "Same SD-Turbo 2.1 at 4-bit. Smallest download for tight storage; slightly lower quality.",
            modelId = "gpustack/stable-diffusion-v2-1-turbo-GGUF",
            modelFile = "stable-diffusion-v2-1-turbo-Q4_0.gguf",
            sizeBytes = 2_190_000_000,
            license = "other",
            minDeviceMemoryGb = 3,
            tags = listOf("fast", "distilled", "compact"),
        ),
        SdCatalogEntry(
            displayName = "SDXL-Turbo (Q4_0)",
            family = "sdxlturbo",
            format = "gguf",
            description = "SDXL Turbo, 1-step distilled at 4-bit. Higher fidelity and 1024px-capable; needs 6 GB+ free RAM.",
            modelId = "gpustack/stable-diffusion-xl-1.0-turbo-GGUF",
            modelFile = "stable-diffusion-xl-1.0-turbo-Q4_0.gguf",
            sizeBytes = 3_600_000_000,
            license = "sai-nc-community",
            minDeviceMemoryGb = 6,
            tags = listOf("sdxl", "distilled"),
        ),
        SdCatalogEntry(
            displayName = "SDXL-Turbo (Q8_0)",
            family = "sdxlturbo",
            format = "gguf",
            description = "SDXL Turbo at 8-bit. Best quality on flagship devices; needs 8 GB+ free RAM.",
            modelId = "gpustack/stable-diffusion-xl-1.0-turbo-GGUF",
            modelFile = "stable-diffusion-xl-1.0-turbo-Q8_0.gguf",
            sizeBytes = 6_500_000_000,
            license = "sai-nc-community",
            minDeviceMemoryGb = 8,
            tags = listOf("sdxl", "distilled", "high-quality"),
        ),
    )

    fun findById(modelId: String): SdCatalogEntry? =
        ENTRIES.firstOrNull { it.modelId == modelId }

    fun findByFamily(family: String): List<SdCatalogEntry> =
        ENTRIES.filter { it.family == family }
}
