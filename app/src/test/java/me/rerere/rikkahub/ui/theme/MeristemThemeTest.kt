package me.rerere.rikkahub.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeristemThemeTest {
    @Test
    fun `meristem appears in preset theme selection without replacing defaults`() {
        val family = requireNotNull(findThemeFamily("meristem"))
        assertEquals("slate", family.defaultVariation.id)
        assertEquals("blue", family.defaultAccent.id)
        assertEquals(listOf("slate", "warm"), family.variations.map { it.id })
        assertEquals(
            listOf("blue", "teal", "green", "yellow", "peach", "red", "mauve", "pink"),
            family.accents.map { it.id },
        )
        assertTrue(PresetThemes.any { it.id == "meristem" })
        assertEquals(listOf("meristem"), PresetThemes.map { it.id })
        assertEquals("meristem", findPresetTheme("sakura").id)
        assertEquals("meristem", findPresetTheme("dracula").id)
        assertEquals("meristem", findPresetTheme("unknown-theme").id)
    }

    @Test
    fun `meristem provides distinct color schemes for light and dark modes`() {
        val family = requireNotNull(findThemeFamily("meristem"))
        val light = family.colorScheme("slate", "blue", dark = false)
        val dark = family.colorScheme("slate", "blue", dark = true)
        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.onSurface, dark.onSurface)
        assertNotEquals(light.primary, family.colorScheme("slate", "teal", dark = false).primary)
    }
}
