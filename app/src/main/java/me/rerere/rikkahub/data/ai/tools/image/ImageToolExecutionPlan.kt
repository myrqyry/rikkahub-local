package me.rerere.rikkahub.data.ai.tools.image

import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelSource

data class ImageToolExecutionPlan(
    val operation: ImageOperation,
    val model: ModelDescriptor,
    val providerSetting: ProviderSetting,
    val inputMedia: List<ResolvedMedia> = emptyList(),
    val sendsUserMedia: Boolean,
    val createsArtifact: Boolean,
    val source: ModelSource,
)

object ImageToolCatalog {
    private val ALWAYS_ASK = setOf("generate_image", "edit_image")
    val TOOL_NAMES = setOf("generate_image", "edit_image", "analyze_image", "extract_text_from_image")

    fun requiresApproval(toolName: String): Boolean = toolName in ALWAYS_ASK

    fun artifactIdFor(galleryId: Int): String = "img_$galleryId"

    fun galleryIdFrom(artifactId: String): Int? =
        if (artifactId.startsWith("img_")) artifactId.removePrefix("img_").toIntOrNull() else null
}
