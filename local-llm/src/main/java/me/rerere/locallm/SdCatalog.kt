package me.rerere.locallm

/**
 * Recommended generation profile for a catalog model.
 *
 * Values are verified against each model's card: both distilled Turbo families are
 * 1-to-4-step models and should never inherit the generic 20-step / CFG-7 defaults of
 * ordinary SD/SDXL. Provider settings are treated as user overrides and win when they
 * differ from the factory defaults.
 */
data class SdGenerationProfile(
    val defaultWidth: Int = 512,
    val defaultHeight: Int = 512,
    val minSteps: Int = 1,
    val maxSteps: Int = 4,
    val defaultSteps: Int = 1,
    val defaultCfgScale: Float = 0f,
    val samplerOverride: String? = null,
)

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
    val generationProfile: SdGenerationProfile? = null,
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
            generationProfile = SdGenerationProfile(
                defaultWidth = 512,
                defaultHeight = 512,
                minSteps = 1,
                maxSteps = 4,
                defaultSteps = 1,
                defaultCfgScale = 0f,
            ),
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
            generationProfile = SdGenerationProfile(
                defaultWidth = 512,
                defaultHeight = 512,
                minSteps = 1,
                maxSteps = 4,
                defaultSteps = 1,
                defaultCfgScale = 0f,
            ),
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
            generationProfile = SdGenerationProfile(
                defaultWidth = 1024,
                defaultHeight = 1024,
                minSteps = 1,
                maxSteps = 4,
                defaultSteps = 1,
                defaultCfgScale = 0f,
            ),
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
            generationProfile = SdGenerationProfile(
                defaultWidth = 1024,
                defaultHeight = 1024,
                minSteps = 1,
                maxSteps = 4,
                defaultSteps = 1,
                defaultCfgScale = 0f,
            ),
        ),
    )

    fun findById(modelId: String): SdCatalogEntry? =
        ENTRIES.firstOrNull { it.modelId == modelId }

    fun findByModelFile(modelFile: String): SdCatalogEntry? =
        ENTRIES.firstOrNull { it.modelFile == modelFile }

    fun findByFamily(family: String): List<SdCatalogEntry> =
        ENTRIES.filter { it.family == family }
}
