package me.rerere.locallm.litert.browser

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun roundTrip(command: BrowserCommand): BrowserCommand {
        val encoded = json.encodeToString(BrowserCommand.serializer(), command)
        return json.decodeFromString(BrowserCommand.serializer(), encoded)
    }

    @Test
    fun `all command variants round-trip with their discriminators`() {
        val commands: List<BrowserCommand> = listOf(
            BrowserCommand.Navigate("https://example.com"),
            BrowserCommand.Click("#submit"),
            BrowserCommand.Type("#name", "Mat"),
            BrowserCommand.Scroll("down", 400),
            BrowserCommand.Back,
            BrowserCommand.Forward,
            BrowserCommand.Submit("form#login"),
            BrowserCommand.Select("select#mode", "fast"),
            BrowserCommand.WaitFor("#loaded", "visible", containsText = null),
            BrowserCommand.Snapshot,
            BrowserCommand.EvalJs("document.title"),
            BrowserCommand.Done,
        )
        commands.forEach { command -> assertEquals(command, roundTrip(command)) }
    }

    @Test
    fun `browser ref stringifies as browser prefix`() {
        assertEquals("browser:conv-1", BrowserRef("conv-1").toString())
    }
}
