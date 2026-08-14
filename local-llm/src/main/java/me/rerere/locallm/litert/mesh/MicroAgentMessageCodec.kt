package me.rerere.locallm.litert.mesh

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serialization helper for [MicroAgentMessage] protocol bodies riding inside a
 * [MicroAgentEvent.payload]. The mesh stays transport; bodies stay typed protocol.
 */
object MicroAgentMessageCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(message: MicroAgentMessage): String =
        json.encodeToString<MicroAgentMessage>(message)

    fun decode(payloadJson: String): MicroAgentMessage =
        json.decodeFromString<MicroAgentMessage>(payloadJson)
}
