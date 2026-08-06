package me.rerere.rikkahub.data.ai.tools.image

import kotlinx.serialization.Serializable

@Serializable
enum class ImageOperation {
    IMAGE_GENERATION,
    IMAGE_EDIT,
    IMAGE_ANALYSIS,
    TEXT_EXTRACTION,
}

@Serializable
data class StoredImageArtifact(
    val artifactId: String,
    val path: String,
    val uri: String,
    val galleryId: Int,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

@Serializable
data class ImageToolResult(
    val success: Boolean,
    val operation: ImageOperation,
    val artifacts: List<StoredImageArtifact> = emptyList(),
    val modelId: String,
    val providerId: String? = null,
    val executionSource: String? = null,
)

@Serializable
data class ImageToolResultMetadata(
    val operation: ImageOperation,
    val artifacts: List<StoredImageArtifact> = emptyList(),
    val prompt: String? = null,
    val sourceArtifactIds: List<String> = emptyList(),
    val modelId: String,
    val providerId: String? = null,
)
