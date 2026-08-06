package me.rerere.rikkahub.data.ai.tools.image

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
data class ImageToolError(
    val code: String,
    val detail: String? = null,
    val recovery: JsonObject? = null,
)

/**
 * 图片工具 (generate_image / edit_image / analyze_image / extract_text_from_image)
 * 的规范机器可读结果信封, 作为 Text 部件输出. 文本表示仅用于 model/headless 兼容:
 * UI 渲染器直接解码本类型, 不得解析展示文本或推断字段.
 */
@Serializable
data class ImageToolResult(
    val schemaVersion: Int = 1,
    val success: Boolean,
    val operation: ImageOperation,
    val artifacts: List<StoredImageArtifact> = emptyList(),
    val modelId: String? = null,
    val providerId: String? = null,
    val executionSource: String? = null,
    val error: ImageToolError? = null,
)
