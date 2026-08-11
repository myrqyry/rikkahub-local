package me.rerere.rikkahub.ui.theme

import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeFamilyTest {
    @Test
    fun registryContainsAllThemeKitFamilies() {
        assertTrue(ThemeFamilies.map { it.id }.containsAll(listOf(
            "dracula",
            "catppuccin",
            "rose_pine",
            "tokyo_night",
            "gruvbox_dark",
        )))
    }

    @Test
    fun selectingActiveVariationCyclesWithWraparound() {
        val family = findThemeFamily("dracula")!!

        assertEquals("soft", nextThemeVariation(family, "default"))
        assertEquals("high_contrast", nextThemeVariation(family, "soft"))
        assertEquals("default", nextThemeVariation(family, "high_contrast"))
    }

    @Test
    fun longPressAccentCyclesWithWraparound() {
        val family = findThemeFamily("tokyo_night")!!

        assertEquals("blue", nextThemeAccent(family, "purple"))
        assertEquals("purple", nextThemeAccent(family, "red"))
    }

    @Test
    fun unknownSelectionsStartAtTheFirstEntry() {
        val family = findThemeFamily("catppuccin")!!

        assertEquals("macchiato", nextThemeVariation(family, "missing"))
        assertEquals("blue", nextThemeAccent(family, "missing"))
    }

    @Test
    fun generatedFamilyHasLightAndDarkSchemes() {
        val family = findThemeFamily("rose_pine")!!
        val light = family.colorScheme("default", "iris", dark = false)
        val dark = family.colorScheme("default", "iris", dark = true)

        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.onBackground, dark.onBackground)
    }

    @Test
    fun newSettingsUseCompatibleThemeDefaults() {
        val settings = Settings()

        assertEquals("default", settings.themeVariation)
        assertEquals("default", settings.themeAccent)
        assertEquals(null, settings.materialYouSourceColor)
    }
}
