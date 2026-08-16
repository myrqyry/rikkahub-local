package me.rerere.rikkahub.data.ai

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.ui.RikkaUi
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationHandlerLiftTest {

    private fun successTool(id: String, ui: RikkaUi): UIMessagePart.Tool {
        val input = Json.encodeToString(
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("ui_column"),
                    "children" to JsonArray(emptyList()),
                )
            )
        )
        return UIMessagePart.Tool(
            toolCallId = id,
            toolName = "render_ui",
            input = input,
            output = listOf(UIMessagePart.Text("""{"ok":true,"rendered":true}""")),
        )
    }

    @Test
    fun `successful render_ui produces generated ui after tool block`() {
        val tools = listOf(successTool("t1", RikkaUi.Text("x")))
        val lifted = liftRenderedUi(tools)
        assertEquals(2, lifted.size)
        assertTrue(lifted.last() is UIMessagePart.GeneratedUi)
        assertEquals("t1", (lifted.last() as UIMessagePart.GeneratedUi).renderId)
    }

    @Test
    fun `failed receipt never creates generated ui`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "t1",
            toolName = "render_ui",
            input = "{}",
            output = listOf(UIMessagePart.Text("""{"ok":false,"error":"invalid_ui_tree"}""")),
        )
        assertEquals(1, liftRenderedUi(listOf(tool)).size)
    }

    @Test
    fun `consecutive tools stay contiguous`() {
        val tools = listOf(successTool("a", RikkaUi.Text("x")), successTool("b", RikkaUi.Text("y")))
        val lifted = liftRenderedUi(tools)
        assertEquals(4, lifted.size)
        assertEquals("a", (lifted[0] as UIMessagePart.Tool).toolCallId)
        assertEquals("b", (lifted[1] as UIMessagePart.Tool).toolCallId)
        assertTrue(lifted[2] is UIMessagePart.GeneratedUi)
        assertTrue(lifted[3] is UIMessagePart.GeneratedUi)
    }

    @Test
    fun `distinct render ids and idempotent re-run`() {
        val tools = listOf(successTool("a", RikkaUi.Text("x")))
        val once = liftRenderedUi(tools)
        val twice = liftRenderedUi(once)
        assertEquals(2, once.size)
        assertEquals(2, twice.size) // no duplicate renderId added
    }
}
