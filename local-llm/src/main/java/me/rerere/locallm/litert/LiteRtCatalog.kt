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
     * Each card installs directly: the Install button downloads the referenced file into
     * app storage and registers it, so the user does not have to fetch it manually. A card
     * also keeps a link to the model's HuggingFace page for gated repos, whose token-less
     * download returns 401 and must be fetched from the page instead.
     *
     * Curation criteria — an entry stays ONLY if BOTH hold:
     *  1. **Reachable model page.** The HF repo must exist and host the referenced .litertlm
     *     file. Verified present: litert-community/gemma-4-E2B-it-litert-lm,
     *     litert-community/gemma-4-E4B-it-litert-lm, litert-community/Qwen2.5-1.5B-Instruct,
     *     litert-community/functiongemma-270m-ft-mobile-actions (gated=auto, gemma license —
     *     tool-calling capable; gating is irrelevant under the link-only policy),
     *     litert-community/SmolVLM2-500M, litert-community/FastVLM-0.5B, litert-community/
     *     PaddleOCR-VL-1.6, litert-community/InternVL3-1B (multimodal vision models —
     *     OCR-capable, see tags), litert-community/LFM2.5-1.2B-Instruct,
     *     litert-community/MiniCPM5-2B (tool-calling templates verified in the shipped bundle).
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
        LiteRtCatalogEntry(
            displayName = "LFM2.5-1.2B-Instruct",
            modelId = "litert-community/LFM2.5-1.2B-Instruct",
            modelFile = "LFM2.5-1.2B-Instruct_int4.litertlm",
            description = "LiquidAI's LFM2.5-1.2B-Instruct ready for deployment on Android using LiteRT-LM. Compact and tool-calling capable — its chat template ships with tool support.",
            sizeBytes = 736015744L,
            minDeviceMemoryGb = 6,
            recommended = false,
            tags = listOf("tool-calling"),
        ),
        LiteRtCatalogEntry(
            displayName = "MiniCPM5-2B",
            modelId = "litert-community/MiniCPM5-2B",
            modelFile = "MiniCPM5-2B_int4.litertlm",
            description = "OpenBMB's MiniCPM5-2B ready for deployment on Android using LiteRT-LM. Native tool-calling support in the chat template, with an optional thinking channel.",
            sizeBytes = 1553670064L,
            minDeviceMemoryGb = 6,
            recommended = false,
            tags = listOf("tool-calling", "thinking"),
        ),
        // Multimodal vision models — these double as on-device OCR: pick one as the OCR
        // model (Settings → Model → OCR) and images get transcribed locally instead of
        // hitting a cloud vision API. Not tool-tuned like the LLM entries above, so use
        // them for image understanding, not the agent tool loop.
        LiteRtCatalogEntry(
            displayName = "SmolVLM2-500M",
            modelId = "litert-community/SmolVLM2-500M",
            modelFile = "SmolVLM2-500M.litertlm",
            description = "A variant of HuggingFace's SmolVLM2-500M-Instruct ready for deployment on Android using LiteRT-LM. Lightweight multimodal VLM — ideal for on-device OCR and image understanding.",
            sizeBytes = 361052336L,
            minDeviceMemoryGb = 6,
            recommended = false,
            tags = listOf("multimodal", "ocr"),
        ),
        LiteRtCatalogEntry(
            displayName = "FastVLM-0.5B",
            modelId = "litert-community/FastVLM-0.5B",
            modelFile = "FastVLM-0.5B.litertlm",
            description = "FastVLM-0.5B built for LiteRT-LM on Android. Fast, efficient multimodal vision model for on-device OCR and image understanding.",
            sizeBytes = 1156342768L,
            minDeviceMemoryGb = 8,
            recommended = false,
            tags = listOf("multimodal", "ocr"),
        ),
        LiteRtCatalogEntry(
            displayName = "PaddleOCR-VL-1.6",
            modelId = "litert-community/PaddleOCR-VL-1.6",
            modelFile = "PaddleOCR-VL-1.6.litertlm",
            description = "PaddleOCR-VL-1.6 for Android via LiteRT-LM. A dedicated document-OCR vision model — the strongest on-device pick for transcribing text from images and scans.",
            sizeBytes = 1390305472L,
            minDeviceMemoryGb = 6,
            recommended = false,
            tags = listOf("multimodal", "ocr"),
        ),
        LiteRtCatalogEntry(
            displayName = "InternVL3-1B",
            modelId = "litert-community/InternVL3-1B",
            modelFile = "InternVL3-1B.litertlm",
            description = "InternVL3-1B ready for Android via LiteRT-LM. Small general-purpose VLM for image understanding and OCR, with more capacity than the 500M-class models.",
            sizeBytes = 737314160L,
            minDeviceMemoryGb = 6,
            recommended = false,
            tags = listOf("multimodal", "ocr"),
        ),
    )

    /** Find an entry by modelFile (matches what's stored in our provider config). Useful for
     *  rendering "you have <X> installed" in the UI. */
    fun findByModelFile(modelFile: String): LiteRtCatalogEntry? =
        ENTRIES.firstOrNull { it.modelFile == modelFile }
}
