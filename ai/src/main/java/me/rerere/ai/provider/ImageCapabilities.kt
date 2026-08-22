package me.rerere.ai.provider

import me.rerere.ai.ui.ImageAspectRatio

/**
 * Phase image-gen refinement (Task 2). Explicit capability negotiation for image runtimes.
 * The UI/agent tool layer clamps controls and validates requests against these, so an
 * unsupported control can never silently do nothing.
 */
data class ImageCapabilities(
    val generation: Boolean,
    val editing: Boolean,
    val maxOutputs: Int,
    val supportedAspectRatios: Set<ImageAspectRatio>,
    val supportsSeed: Boolean,
    val supportsNegativePrompt: Boolean,
    val supportsSteps: Boolean,
    val supportsCfg: Boolean,
    val supportsPartialPreview: Boolean,
    val maxReferenceImages: Int,
) {
    /** Keeps only the aspect ratios this runtime actually supports. */
    fun filterAspectRatios(requested: Set<ImageAspectRatio>): Set<ImageAspectRatio> =
        requested.filterTo(linkedSetOf()) { it in supportedAspectRatios }
}

/** Per-runtime capabilities; local Stable Diffusion is deliberately bounded. */
val ProviderSetting.imageCapabilities: ImageCapabilities
    get() = when (this) {
        is ProviderSetting.StableDiffusion -> ImageCapabilities(
            generation = true,
            editing = false,
            maxOutputs = 4,
            supportedAspectRatios = ImageAspectRatio.entries.toSet(),
            supportsSeed = true,
            supportsNegativePrompt = true,
            supportsSteps = true,
            supportsCfg = true,
            supportsPartialPreview = false,
            maxReferenceImages = 0,
        )
        else -> ImageCapabilities(
            generation = true,
            editing = true,
            maxOutputs = 4,
            supportedAspectRatios = ImageAspectRatio.entries.toSet(),
            supportsSeed = true,
            supportsNegativePrompt = true,
            supportsSteps = true,
            supportsCfg = true,
            supportsPartialPreview = true,
            maxReferenceImages = 4,
        )
    }
