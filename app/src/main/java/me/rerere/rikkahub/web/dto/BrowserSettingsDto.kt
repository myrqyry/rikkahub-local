package me.rerere.rikkahub.web.dto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant

private val sensitiveSettingKeys = setOf(
    "accessKeyId",
    "accessToken",
    "apiKey",
    "authorization",
    "bearerToken",
    "clientSecret",
    "credentials",
    "customBodies",
    "customHeaders",
    "headers",
    "oauth",
    "password",
    "privateKey",
    "refreshToken",
    "secret",
    "secretAccessKey",
    "webServerAccessPassword",
)

fun Settings.toBrowserSafeJson(): JsonObject {
    val safe = JsonInstant.encodeToJsonElement(this).removeSensitiveSettings()?.jsonObject
        ?: buildJsonObject { }
    return buildJsonObject {
        safe.forEach { (key, value) -> put(key, value) }
        put("webServerPasswordConfigured", webServerAccessPassword.isNotBlank())
    }
}

/** Preserve internal credentials when a browser sends back a redacted settings snapshot. */
fun mergeBrowserSettings(current: JsonObject, incoming: JsonObject): JsonObject = buildJsonObject {
    current.forEach { (key, value) -> put(key, value) }
    incoming.forEach { (key, value) ->
        if (key !in sensitiveSettingKeys) {
            put(key, mergeBrowserSettingValue(current[key], value))
        }
    }
}

private fun mergeBrowserSettingValue(current: JsonElement?, incoming: JsonElement): JsonElement {
    if (current is JsonObject && incoming is JsonObject) {
        return mergeBrowserSettings(current, incoming)
    }
    if (current is JsonArray && incoming is JsonArray) {
        val currentById = current.mapNotNull { item ->
            (item as? JsonObject)?.get("id")?.let { id -> id to item }
        }.toMap()
        if (incoming.all { it is JsonObject && it["id"] != null }) {
            return buildJsonArray {
                incoming.forEach { item ->
                    val incomingObject = item as JsonObject
                    add(mergeBrowserSettingValue(currentById[incomingObject["id"]], incomingObject))
                }
            }
        }
    }
    return incoming
}

private fun JsonElement.removeSensitiveSettings(key: String? = null): JsonElement? {
    if (key != null && key in sensitiveSettingKeys) return null
    return when (this) {
        is JsonObject -> buildJsonObject {
            forEach { (childKey, value) ->
                value.removeSensitiveSettings(childKey)?.let { put(childKey, it) }
            }
        }
        is JsonArray -> buildJsonArray {
            forEach { value -> value.removeSensitiveSettings()?.let { add(it) } }
        }
        else -> this
    }
}
