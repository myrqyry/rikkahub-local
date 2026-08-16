package me.rerere.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.RikkaUi
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderUiToolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `valid ui returns ok receipt`() = runBlocking {
        val ui: RikkaUi = RikkaUi.Form("s", listOf(RikkaUi.Text("hi")))
        val input = json.encodeToString(RikkaUi.serializer(), ui)
        val parts = renderUiTool.execute(Json.parseToJsonElement(input))
        assertEquals(1, parts.size)
        assertTrue((parts.single() as UIMessagePart.Text).text.contains("\"ok\":true"))
    }

    @Test
    fun `invalid ui returns error envelope`() = runBlocking {
        val ui: RikkaUi = RikkaUi.Progress(2.0f)
        val input = json.encodeToString(RikkaUi.serializer(), ui)
        val parts = renderUiTool.execute(Json.parseToJsonElement(input))
        assertEquals(1, parts.size)
        assertTrue((parts.single() as UIMessagePart.Text).text.contains("error"))
    }

    @Test
    fun `generated ui never appears in tool output`() = runBlocking {
        val ui: RikkaUi = RikkaUi.Text("hi")
        val input = json.encodeToString(RikkaUi.serializer(), ui)
        val parts = renderUiTool.execute(Json.parseToJsonElement(input))
        assertTrue(parts.none { it is UIMessagePart.GeneratedUi })
    }
}
