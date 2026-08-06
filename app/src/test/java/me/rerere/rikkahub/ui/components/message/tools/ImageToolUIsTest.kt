package me.rerere.rikkahub.ui.components.message.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.data.ai.tools.image.StoredImageArtifact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 仅测试 [decodeImageToolResult] / [isImageToolResultRenderable] 两个纯函数:
 * 无 mocks, 无 Compose 测试依赖. 回退决策由 [ImageToolCardRenderer.Preview] 负责.
 */
class ImageToolUIsTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun decode(raw: String) = decodeImageToolResult(json.parseToJsonElement(raw), json)

    @Test
    fun `valid result decodes to typed result with artifacts`() {
        val result = decode(
            """
            {
              "schemaVersion": 1,
              "success": true,
              "operation": "IMAGE_GENERATION",
              "artifacts": [
                {
                  "artifactId": "img_7",
                  "path": "/p/1.png",
                  "uri": "file:///p/1.png",
                  "galleryId": 7,
                  "mimeType": "image/png",
                  "width": 512,
                  "height": 512
                }
              ]
            }
            """.trimIndent()
        )
        assertTrue(result != null)
        assertEquals(1, result?.schemaVersion)
        assertEquals(listOf("img_7"), result?.artifacts?.map { it.artifactId })
        assertEquals("file:///p/1.png", result?.artifacts?.firstOrNull()?.uri)
        assertTrue(isImageToolResultRenderable(result))
    }

    @Test
    fun `garbage or malformed json element decodes to null`() {
        assertNull(decodeImageToolResult(JsonPrimitive("garbage"), json))
        assertNull(decodeImageToolResult(JsonNull, json))
        // 缺少必填 operation 字段: 解码失败 -> null
        assertNull(decode("""{"success":true}"""))
    }

    @Test
    fun `null content decodes to null`() {
        assertNull(decodeImageToolResult(null, json))
    }

    @Test
    fun `error result decodes with success false`() {
        val result = decode(
            """
            {
              "success": false,
              "operation": "IMAGE_GENERATION",
              "error": { "code": "provider_failed", "detail": "boom" }
            }
            """.trimIndent()
        )
        assertEquals(false, result?.success)
        assertEquals("provider_failed", result?.error?.code)
        assertEquals("boom", result?.error?.detail)
        assertFalse(isImageToolResultRenderable(result))
    }

    @Test
    fun `empty artifacts decodes with empty list`() {
        val result = decode("""{"success":true,"operation":"IMAGE_GENERATION","artifacts":[]}""")
        assertTrue(result != null)
        assertEquals(emptyList<StoredImageArtifact>(), result?.artifacts)
        assertFalse(isImageToolResultRenderable(result))
    }

    @Test
    fun `future schema version still decodes but is not renderable`() {
        val result = decode("""{"schemaVersion":99,"success":true,"operation":"IMAGE_GENERATION"}""")
        assertEquals(99, result?.schemaVersion)
        // 解码器不负责版本回退决策: 对象必须返回, 由 Preview 里的 renderable 检查决定回退
        assertTrue(result != null)
        assertFalse(isImageToolResultRenderable(result))
    }
}
