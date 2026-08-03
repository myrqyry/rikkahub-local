package me.rerere.locallm.litert

/** A single curated entry the Settings → Local · LiteRT picker shows. Joins the
 *  download identity (HuggingFace repo + file) with the config defaults so picking
 *  an entry is one-shot: download + config in one user action. */
data class LiteRtCatalogEntry(
    val displayName: String,         // e.g. "Gemma 4 E2B-it"
    val modelId: String,             // HuggingFace repo path, e.g. "litert-community/gemma-4-E2B-it-litert-lm"
    val modelFile: String,           // File inside the repo, e.g. "gemma-4-E2B-it.litertlm"
    val description: String,         // Markdown-friendly one-liner from Gallery
    val sizeBytes: Long,
    val minDeviceMemoryGb: Int,
    val recommended: Boolean = false, // Marks the "default first pick" — Gemma3-1B-IT for low RAM, Gemma-4-E2B for capable
    val tags: List<String> = emptyList(), // ["multimodal", "thinking", "speculative-decoding"] — for chips in UI
) {
    /** Pre-built download URL on HuggingFace's `resolve` path. Same format ModelInstall already validates. */
    fun resolveUrl(): String = "https://huggingface.co/$modelId/resolve/main/$modelFile"
    /** Where the user obtains this model — shown in the catalog UI and opened via ACTION_VIEW. */
    val sourceUrl: String get() = "https://huggingface.co/$modelId"
    /** Lookup the matching config defaults. */
    fun config(): LiteRtModelConfig = LiteRtModelDefaults.forModelFile(modelFile)
}

object LiteRtCatalog {
    /**
     * Curated picker list — order matters (top of list shown first).
     *
     * These are links, not installers: each card opens the model's HuggingFace page
     * (`sourceUrl`), and the user gets the file themselves — by copying the URL into the
     * paste-install field, or downloading it and importing from the filesystem. The app never
     * downloads a catalog model directly, so repo gating (401 for token-less downloads) is
     * irrelevant to curation: a gated repo is still a perfectly good pick.
     *
     * Curation criteria — an entry stays ONLY if BOTH hold:
     *  1. **Reachable model page.** The HF repo must exist and host the referenced .litertlm
     *     file. Verified present: litert-community/gemma-4-E2B-it-litert-lm,
     *     litert-community/gemma-4-E4B-it-litert-lm, litert-community/Qwen2.5-1.5B-Instruct,
     *     litert-community/functiongemma-270m-ft-mobile-actions (gated=auto, gemma license —
     *     a first-class tool-calling fit despite being gated).
     *  2. **Tool-calling capable.** RikkaHub drives these models through the prompt-engineered
     *     tool protocol in LiteRtToolPrefix, so the model must be instruction-tuned for tool /
     *     function calling. Dropped on this rule: DeepSeek-R1-Distill-Qwen-1.5B (a reasoning
     *     distillation — it does not emit the <tool_call> blocks the agent loop depends on).
     */
    val ENTRIES: List<LiteRtCatalogEntry> = listOf(
        LiteRtCatalogEntry(
            displayName = "Gemma-4-E2B-it",
            modelId = "litert-community/gemma-4-E2B-it-litert-lm",
            modelFile = "gemma-4-E2B-it.litertlm",
            description = "A variant of Gemma 4 E2B ready for deployment on Android using LiteRT-LM. It supports multi-modality input, with up to 32K context length.",
            sizeBytes = 2588147712L,
            minDeviceMemoryGb = 8,
            recommended = true,
            tags = listOf("multimodal", "thinking", "speculative-decoding"),
        ),
        LiteRtCatalogEntry(
            displayName = "Gemma-4-E4B-it",
            modelId = "litert-community/gemma-4-E4B-it-litert-lm",
            modelFile = "gemma-4-E4B-it.litertlm",
            description = "A variant of Gemma 4 E4B ready for deployment on Android using LiteRT-LM. It supports multi-modality input, with up to 32K context length.",
            sizeBytes = 3659530240L,
            minDeviceMemoryGb = 12,
            recommended = false,
            tags = listOf("multimodal", "thinking", "speculative-decoding"),
        ),
        LiteRtCatalogEntry(
            displayName = "Qwen2.5-1.5B-Instruct",
            modelId = "litert-community/Qwen2.5-1.5B-Instruct",
            modelFile = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            description = "A variant of Qwen/Qwen2.5-1.5B-Instruct ready for deployment on Android using LiteRT-LM.",
            sizeBytes = 1597931520L,
            minDeviceMemoryGb = 6,
            recommended = true,
            tags = emptyList(),
        ),
        LiteRtCatalogEntry(
            displayName = "FunctionGemma 270M",
            modelId = "litert-community/functiongemma-270m-ft-mobile-actions",
            modelFile = "mobile_actions_q8_ekv1024.litertlm",
            description = "A finetune of Google's FunctionGemma 270M for on-device tool / function calling, built for LiteRT-LM. Gated repo (Gemma license) — grab the file from the model page, then import it.",
            sizeBytes = 288964608L,
            minDeviceMemoryGb = 4,
            recommended = false,
            tags = listOf("tool-calling"),
        ),
    )

    /** Find an entry by modelFile (matches what's stored in our provider config). Useful for
     *  rendering "you have <X> installed" in the UI. */
    fun findByModelFile(modelFile: String): LiteRtCatalogEntry? =
        ENTRIES.firstOrNull { it.modelFile == modelFile }
}
