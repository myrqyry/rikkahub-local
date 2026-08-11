package me.rerere.rikkahub.data.ai.tools.image

import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.data.ai.tools.safety.DataEgress
import me.rerere.rikkahub.data.ai.tools.safety.ExecutionProvenance
import me.rerere.rikkahub.data.ai.tools.safety.ToolEffect
import me.rerere.rikkahub.data.ai.tools.safety.ToolExecutionPlan

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

fun ImageToolExecutionPlan.toToolExecutionPlan(
    operationId: String,
    toolName: String = operation.name.lowercase(),
    provenance: ExecutionProvenance,
): ToolExecutionPlan {
    val effects = buildSet {
        if (inputMedia.isNotEmpty()) add(ToolEffect.READ_LOCAL_DATA)
        if (sendsUserMedia) {
            add(ToolEffect.SEND_NETWORK_REQUEST)
            add(ToolEffect.UPLOAD_DATA)
        }
        if (createsArtifact) add(ToolEffect.WRITE_LOCAL_DATA)
    }
    val egress = if (sendsUserMedia) {
        listOf(DataEgress(category = "image", destination = "configured_provider", scope = "input_media"))
    } else {
        emptyList()
    }
    return ToolExecutionPlan(
        operationId = operationId,
        toolName = toolName,
        effects = effects,
        dataEgress = egress,
        provenance = provenance,
    )
}
