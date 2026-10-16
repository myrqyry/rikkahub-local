package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatCompletionsStreamNullTest {
    @Test
    fun `compatible SSE streams skip null and primitive lines without discarding valid chunks`() {
        val objects = parseChatCompletionEventObjects("""
            null
            123
            false
            {"id":"r1","choices":[{"delta":{"content":"hello"}}]}
            []
            {"id":"r2","choices":[{"delta":{"content":" world"}}]}
        """.trimIndent())

        assertEquals(listOf("r1", "r2"), objects.map { it["id"]?.jsonPrimitive?.content })
    }

    @Test
    fun `malformed JSON is not silently accepted as an empty stream`() {
        assertThrows(SerializationException::class.java) {
            parseChatCompletionEventObjects("""{"id":""")
        }
    }

    @Test
    fun `null annotations and nullable citation objects do not crash the stream`() {
        val api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod("parseAnnotations", JsonArray::class.java)
        method.isAccessible = true
        val annotations = method.invoke(
            api,
            JsonArray(listOf(
                JsonNull,
                JsonPrimitive("ignored"),
                JsonObject(mapOf(
                    "type" to JsonPrimitive("url_citation"),
                    "url_citation" to JsonNull
                )),
            ))
        ) as List<*>
        assertEquals(1, annotations.size)
        assertEquals(UIMessageAnnotation.UrlCitation("", ""), annotations.single())
    }
}
