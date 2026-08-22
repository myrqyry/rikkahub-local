package me.rerere.rikkahub.data.ai

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.media.MediaArtifactRef

/**
 * Provenance record for a single image generation. Captured alongside the persisted
 * artifact so the image-tool result envelope can report what actually happened: the
 * model and its revision, the runtime/backend (CPU/VULKAN/cloud), the resolved output
 * dimensions and sampling parameters, elapsed sampling time, and any source artifacts
 * (for image edits).
 */
@Serializable
data class GenerationReceipt(
    val artifactId: String,
    val modelId: String,
    val modelRevision: String?,
    val runtime: String,
    val backend: String,
    val width: Int,
    val height: Int,
    val seed: Long?,
    val steps: Int?,
    val cfg: Float?,
    val sampler: String?,
    val scheduler: String?,
    val elapsedMs: Long,
    val sourceArtifacts: List<MediaArtifactRef>,
)
