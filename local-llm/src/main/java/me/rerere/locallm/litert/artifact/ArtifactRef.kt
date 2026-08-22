package me.rerere.locallm.litert.artifact

import kotlinx.serialization.Serializable

@Serializable
enum class ArtifactKind {
    PROCESS_OUTPUT,
    IMAGE,
    DOCUMENT,
    TEXT,
    FILE,
}

@Serializable
data class ArtifactRef(
    val id: String,
    val kind: ArtifactKind,
    val name: String,
    val path: String? = null,
    val uri: String? = null,
    val mimeType: String? = null,
    val byteSize: Long? = null,
    val createdAtMs: Long? = null,
    val metadata: Map<String, String> = emptyMap(),
)
