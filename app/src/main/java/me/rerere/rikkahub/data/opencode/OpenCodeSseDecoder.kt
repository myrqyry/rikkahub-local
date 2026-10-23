package me.rerere.rikkahub.data.opencode

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val MAX_SSE_FRAME_CHARS = 1_048_576

/** SSE framing + workspace/session isolation. Events signal a refetch, never prove completion. */
internal class OpenCodeSseDecoder(
    private val directory: String?,
    private val sessionId: String,
) {
    private val data = StringBuilder()
    private var exceeded = false

    fun accept(line: String): OpenCodeSessionSignal? {
        if (line.isNotEmpty()) {
            if (line.startsWith("data:") && !exceeded) {
                val text = line.removePrefix("data:").removePrefix(" ")
                if (data.length + text.length > MAX_SSE_FRAME_CHARS) {
                    exceeded = true
                    data.clear()
                } else {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(text)
                }
            }
            return null
        }
        val frame = if (exceeded) null else data.toString()
        data.clear()
        exceeded = false
        if (frame.isNullOrBlank()) return null
        val root = runCatching { Json.parseToJsonElement(frame) as? JsonObject }.getOrNull() ?: return null
        val payload = root["payload"] as? JsonObject ?: root
        val kind = payload.text("type") ?: return null
        val details = (payload["properties"] ?: payload["data"]) as? JsonObject ?: return null
        val eventDirectory = root.text("directory") ?: (payload["location"] as? JsonObject)?.text("directory")
        if (directory != null && eventDirectory != null && directory != eventDirectory) return null
        val associatedSession = details.text("sessionID")
            ?: (details["info"] as? JsonObject)?.text("sessionID")
            ?: (details["part"] as? JsonObject)?.text("sessionID")
            ?: (details["request"] as? JsonObject)?.text("sessionID")
        if (associatedSession != sessionId) return null
        return when (kind) {
            "message.updated", "message.part.updated", "message.part.delta", "message.removed" ->
                OpenCodeSessionSignal.MessagesChanged
            "session.status", "session.idle", "session.error" -> OpenCodeSessionSignal.StatusChanged
            "permission.asked", "permission.replied" -> OpenCodeSessionSignal.PermissionsChanged
            else -> null
        }
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
