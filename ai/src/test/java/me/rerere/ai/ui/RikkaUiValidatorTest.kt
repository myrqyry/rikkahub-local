package me.rerere.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RikkaUiValidatorTest {

    @Test
    fun `valid form passes`() {
        val tree = RikkaUi.Form("s", listOf(RikkaUi.Input("name"), RikkaUi.Toggle("enabled", "Enabled")))
        assertEquals(emptyList<String>(), validateRikkaUi(tree))
    }

    @Test
    fun `nested depth beyond six is rejected`() {
        var tree: RikkaUi = RikkaUi.Text("leaf")
        repeat(7) { tree = RikkaUi.Column(listOf(tree)) }
        assertTrue(validateRikkaUi(tree).any { it.contains("depth") })
    }

    @Test
    fun `more than one form is rejected`() {
        val tree = RikkaUi.Form("a", listOf(RikkaUi.Form("b", emptyList())))
        assertTrue(validateRikkaUi(tree).any { it.contains("one") || it.contains("Form") })
    }

    @Test
    fun `duplicate keys are rejected`() {
        val tree = RikkaUi.Form("s", listOf(RikkaUi.Input("k"), RikkaUi.Toggle("k", "x")))
        assertTrue(validateRikkaUi(tree).any { it.contains("unique") })
    }

    @Test
    fun `select with empty or duplicated options rejected`() {
        assertTrue(validateRikkaUi(RikkaUi.Select("s", "S", emptyList())).isNotEmpty())
        assertTrue(validateRikkaUi(RikkaUi.Select("s", "S", listOf("a", "a"))).isNotEmpty())
    }

    @Test
    fun `select initial must be in options`() {
        assertTrue(validateRikkaUi(RikkaUi.Select("s", "S", listOf("a"), "z")).isNotEmpty())
    }

    @Test
    fun `progress fraction must be in range`() {
        assertTrue(validateRikkaUi(RikkaUi.Progress(1.5f)).isNotEmpty())
        assertEquals(emptyList<String>(), validateRikkaUi(RikkaUi.Progress(null)))
        assertEquals(emptyList<String>(), validateRikkaUi(RikkaUi.Progress(0.5f)))
    }

    @Test
    fun `unsafe url scheme rejected`() {
        assertTrue(validateRikkaUi(RikkaUi.Link("x", "javascript:alert(1)")).isNotEmpty())
        assertTrue(validateRikkaUi(RikkaUi.Image("ftp://host/f.png")).isNotEmpty())
    }

    @Test
    fun `navigate destination must be allowlisted`() {
        assertTrue(validateRikkaUi(RikkaUi.Button("x", RikkaUiAction.Navigate("/nonexistent"))).isNotEmpty())
        assertTrue(validateRikkaUi(RikkaUi.Button("x", RikkaUiAction.Navigate("/images"))).isEmpty())
    }

    @Test
    fun `spacing out of range rejected`() {
        assertTrue(validateRikkaUi(RikkaUi.Column(emptyList(), spacing = 64)).isNotEmpty())
    }

    @Test
    fun `interactive keys must live inside a form`() {
        assertTrue(validateRikkaUi(RikkaUi.Input("k")).isNotEmpty())
        assertTrue(validateRikkaUi(RikkaUi.Toggle("k", "x")).isNotEmpty())
        assertTrue(validateRikkaUi(RikkaUi.Select("s", "S", listOf("a"))).isNotEmpty())
        assertEquals(
            emptyList<String>(),
            validateRikkaUi(
                RikkaUi.Form(
                    "f",
                    listOf(
                        RikkaUi.Input("k"),
                        RikkaUi.Toggle("t", "x"),
                        RikkaUi.Select("s", "S", listOf("a")),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `submit must reference the tree form`() {
        val tree = RikkaUi.Form("f", listOf(RikkaUi.Button("Go", RikkaUiAction.Submit("f"))))
        assertEquals(emptyList<String>(), validateRikkaUi(tree))
        val mismatch = RikkaUi.Form("f", listOf(RikkaUi.Button("Go", RikkaUiAction.Submit("other"))))
        assertTrue(validateRikkaUi(mismatch).any { it.contains("submit") })
        // Submit before the Form node in the tree still resolves the form id.
        val ordered = RikkaUi.Column(
            listOf(
                RikkaUi.Button("Go", RikkaUiAction.Submit("f")),
                RikkaUi.Form("f", listOf(RikkaUi.Input("k"))),
            ),
        )
        assertEquals(emptyList<String>(), validateRikkaUi(ordered))
        assertTrue(validateRikkaUi(RikkaUi.Button("Go", RikkaUiAction.Submit("f"))).any { it.contains("without a form") })
    }
}
