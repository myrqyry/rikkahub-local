package me.rerere.rikkahub.ui.theme

import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeFamilyTest {
    @Test
    fun onlyMeristemStaticFamilyRemains() {
        assertEquals(listOf(MERISTEM_THEME_ID), ThemeFamilies.map { it.id })
        assertEquals(listOf(MERISTEM_THEME_ID), PresetThemes.map { it.id })
        listOf("sakura", "ocean", "spring", "autumn", "black", "dracula",
            "catppuccin", "rose_pine", "tokyo_night", "gruvbox_dark").forEach {
            assertNull(findThemeFamily(it))
            assertEquals(MERISTEM_THEME_ID, findPresetTheme(it).id)
        }
    }

    @Test
    fun variationAndAccentCyclingWrapAround() {
        val family = requireNotNull(findThemeFamily(MERISTEM_THEME_ID))
        assertEquals("warm", nextThemeVariation(family, "slate"))
        assertEquals("slate", nextThemeVariation(family, "warm"))
        assertEquals("teal", nextThemeAccent(family, "blue"))
        assertEquals("blue", nextThemeAccent(family, "pink"))
    }

    @Test
    fun unknownSelectionsStartAfterFallbackFirstEntry() {
        val family = requireNotNull(findThemeFamily(MERISTEM_THEME_ID))
        assertEquals("warm", nextThemeVariation(family, "missing"))
        assertEquals("teal", nextThemeAccent(family, "missing"))
    }

    @Test
    fun generatesDistinctLightAndDarkPalettes() {
        val family = requireNotNull(findThemeFamily(MERISTEM_THEME_ID))
        val light = family.colorScheme("slate", "blue", dark = false)
        val dark = family.colorScheme("slate", "blue", dark = true)
        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.onBackground, dark.onBackground)
    }

    @Test
    fun savedCustomPalettesRemainResolvableEvenAfterRetiringBuiltIns() {
        val custom = CustomTheme(id = "saved-user-theme", name = "My palette", primaryColorArgb = 0xFFAF8A77)
        assertEquals(custom.id, findThemeById(custom.id, listOf(custom))?.id)
        assertNull(findThemeById("old-retired-theme", listOf(custom)))
    }

    @Test
    fun settingsKeepMaterialYouDefaultAndPreferMeristemAsStaticFallback() {
        val settings = Settings()
        assertTrue(settings.dynamicColor)
        assertEquals(MERISTEM_THEME_ID, settings.themeId)
        assertNull(settings.materialYouSourceColor)
        assertFalse(Settings(dynamicColor = false).dynamicColor)
    }
}
