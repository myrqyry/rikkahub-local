package me.rerere.ai.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RikkaUiSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `round-trips a nested tree`() {
        val tree: RikkaUi = RikkaUi.Column(
            children = listOf(
                RikkaUi.Text(content = "Heading", title = true),
                RikkaUi.Text(content = "Body with *emphasis*", emphasized = true),
                RikkaUi.Button(
                    label = "Copy it",
                    action = RikkaUiAction.Copy("copied value"),
                ),
                RikkaUi.Chip(label = "Open docs", action = RikkaUiAction.OpenUrl("https://example.com")),
                RikkaUi.Image(url = "https://example.com/pic.png"),
                RikkaUi.List(items = listOf("one", "two", "three")),
                RikkaUi.Divider,
            ),
            spacing = 12,
            verticalAlignment = "center",
        )

        val encoded = json.encodeToString(RikkaUi.serializer(), tree)
        val decoded = json.decodeFromString(RikkaUi.serializer(), encoded)

        assertEquals(tree, decoded)
    }

    @Test
    fun `uses stable discriminator names`() {
        val tree: RikkaUi = RikkaUi.Text(content = "hi")
        val encoded = json.encodeToString(RikkaUi.serializer(), tree)
        assertEquals("""{"type":"ui_text","content":"hi","emphasized":false,"title":false}""", encoded)
    }

    @Test
    fun `actions round-trip with their own discriminators`() {
        val copy: RikkaUiAction = RikkaUiAction.Copy("x")
        val open: RikkaUiAction = RikkaUiAction.OpenUrl("https://example.com")

        assertEquals(
            copy,
            json.decodeFromString(RikkaUiAction.serializer(), json.encodeToString(RikkaUiAction.serializer(), copy)),
        )
        assertEquals(
            open,
            json.decodeFromString(RikkaUiAction.serializer(), json.encodeToString(RikkaUiAction.serializer(), open)),
        )
        assertNotEquals(
            json.encodeToString(RikkaUiAction.serializer(), copy),
            json.encodeToString(RikkaUiAction.serializer(), open),
        )
    }

    @Test
    fun `column serializes its children inline`() {
        val tree: RikkaUi = RikkaUi.Column(
            children = listOf(RikkaUi.Divider, RikkaUi.Text(content = "a")),
        )
        val encoded = json.encodeToString(RikkaUi.serializer(), tree)
        val decoded = json.decodeFromString(RikkaUi.serializer(), encoded)
        assertEquals(tree, decoded)
        assert(encoded.contains("ui_divider"))
    }

    @Test
    fun `all new form primitives round-trip`() {
        val tree: RikkaUi = RikkaUi.Form(
            id = "settings",
            children = listOf(
                RikkaUi.Input(key = "name", placeholder = "Your name", label = "Name", initial = "Mat"),
                RikkaUi.Toggle(key = "enabled", label = "Enabled", initial = true),
                RikkaUi.Select(key = "mode", label = "Mode", options = listOf("fast", "slow"), initial = "fast"),
                RikkaUi.Progress(fraction = 0.5f),
                RikkaUi.Link(label = "Docs", url = "https://example.com/docs"),
            ),
            spacing = 12,
            verticalAlignment = "center",
        )

        val encoded = json.encodeToString(RikkaUi.serializer(), tree)
        val decoded = json.decodeFromString(RikkaUi.serializer(), encoded)

        assertEquals(tree, decoded)
    }

    @Test
    fun `row round-trips with vertical alignment`() {
        val tree: RikkaUi = RikkaUi.Row(
            children = listOf(
                RikkaUi.Text(content = "a"),
                RikkaUi.Text(content = "b"),
            ),
            spacing = 4,
            verticalAlignment = "bottom",
        )

        val encoded = json.encodeToString(RikkaUi.serializer(), tree)
        val decoded = json.decodeFromString(RikkaUi.serializer(), encoded)

        assertEquals(tree, decoded)
        assert(encoded.contains("ui_row"))
        assert(encoded.contains("\"verticalAlignment\":\"bottom\""))
    }

    @Test
    fun `legacy column verticalAlignment still deserializes`() {
        // Encoded by the pre-Phase-G schema where verticalAlignment controlled horizontal placement.
        val legacy = """{"type":"ui_column","children":[{"type":"ui_text","content":"x"}],"spacing":8,"verticalAlignment":"bottom"}"""
        val decoded = json.decodeFromString(RikkaUi.serializer(), legacy)
        assertEquals(
            RikkaUi.Column(children = listOf(RikkaUi.Text(content = "x")), spacing = 8, verticalAlignment = "bottom"),
            decoded,
        )
    }

    @Test
    fun `new actions round-trip with their own discriminators`() {
        val submit: RikkaUiAction = RikkaUiAction.Submit("settings")
        val navigate: RikkaUiAction = RikkaUiAction.Navigate("/images")

        assertEquals(
            submit,
            json.decodeFromString(RikkaUiAction.serializer(), json.encodeToString(RikkaUiAction.serializer(), submit)),
        )
        assertEquals(
            navigate,
            json.decodeFromString(RikkaUiAction.serializer(), json.encodeToString(RikkaUiAction.serializer(), navigate)),
        )
        assertNotEquals(
            json.encodeToString(RikkaUiAction.serializer(), submit),
            json.encodeToString(RikkaUiAction.serializer(), navigate),
        )
        assert(json.encodeToString(RikkaUiAction.serializer(), submit).contains("ui_submit"))
    }
}
