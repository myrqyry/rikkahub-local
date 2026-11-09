package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.tts.provider.TTSProviderSetting

/**
 * Preserve the surviving TTS providers when an old saved list contains a type
 * which the app no longer supports (notably the removed "nekospeak" engine).
 *
 * This is a read-only compatibility layer. We deliberately do not mutate other
 * provider configs, downloaded models or arbitrary user files.
 */
internal fun decodeTtsProvidersTolerant(raw: String): List<TTSProviderSetting> {
    val elements = runCatching {
        JsonInstant.parseToJsonElement(raw) as? JsonArray
    }.getOrNull() ?: return emptyList()

    return elements.mapNotNull { item ->
        val type = ((item as? JsonObject)?.get("type") as? JsonPrimitive)?.content
        if (type == "nekospeak") {
            null
        } else {
            runCatching { JsonInstant.decodeFromJsonElement<TTSProviderSetting>(item) }.getOrNull()
        }
    }
}
