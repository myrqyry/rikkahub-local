package me.rerere.ai.ui

import kotlinx.serialization.Serializable

/**
 * The transport payload for a generated image. Providers emit the cheapest
 * representation they can produce; consumers write the file directly instead of
 * round-tripping through base64.
 */
@Serializable
sealed interface GeneratedImagePayload {
    val mimeType: String

    @Serializable
    data class Bytes(
        val bytes: ByteArray,
        override val mimeType: String,
    ) : GeneratedImagePayload

    @Serializable
    data class File(
        val path: String,
        override val mimeType: String,
    ) : GeneratedImagePayload

    @Serializable
    data class Base64(
        val data: String,
        override val mimeType: String,
    ) : GeneratedImagePayload
}
