package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import hct.Hct
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalPaletteTest {
    private val wallpaperDark = CustomTheme(primaryColorArgb = 0xFF7867CCL).generateColorScheme(true)
    private val blue = 0xFF44A3EAL

    @Test
    fun wallpaperSchemeRemainsUnchangedWithoutOverrides() {
        assertEquals(wallpaperDark, personalPalette(true, wallpaperDark, null, null))
    }

    @Test
    fun manualBackgroundTintsSurfacesNotOnlyButtons() {
        val neutral = CustomTheme(primaryColorArgb = blue).generateColorScheme(true)
        val personalized = personalPalette(true, wallpaperDark, blue, null)
        assertNotEquals(neutral.background, personalized.background)
        assertNotEquals(neutral.surfaceContainer, personalized.surfaceContainer)
        assertNotEquals(wallpaperDark.background, personalized.background)
        assertTrue(
            "Manual background should have appreciable chroma",
            Hct.fromInt(personalized.background.toArgb()).chroma >
                Hct.fromInt(neutral.background.toArgb()).chroma
        )
    }

    @Test
    fun accentOverrideKeepsEntireBackgroundSurfaceLadder() {
        val manual = personalPalette(true, wallpaperDark, blue, null)
        val override = personalPalette(true, wallpaperDark, blue, 0xFFE9A86DL)
        assertEquals(manual.background, override.background)
        assertEquals(manual.surface, override.surface)
        assertEquals(manual.surfaceContainerLow, override.surfaceContainerLow)
        assertEquals(manual.surfaceContainer, override.surfaceContainer)
        assertEquals(manual.surfaceContainerHighest, override.surfaceContainerHighest)
        assertNotEquals(manual.primary, override.primary)
        assertNotEquals(manual.primaryContainer, override.primaryContainer)
    }

    @Test
    fun wallpaperAccentOverrideDoesNotLoseAndroidWallpaperSurfaces() {
        val overridden = personalPalette(true, wallpaperDark, null, blue)
        assertEquals(wallpaperDark.background, overridden.background)
        assertEquals(wallpaperDark.surfaceContainer, overridden.surfaceContainer)
        assertNotEquals(wallpaperDark.primary, overridden.primary)
    }

    @Test
    fun manualDarkAndLightRemainDistinct() {
        val lightWallpaper = CustomTheme(primaryColorArgb = 0xFF7867CCL).generateColorScheme(false)
        val dark = personalPalette(true, wallpaperDark, blue, null)
        val light = personalPalette(false, lightWallpaper, blue, null)
        assertNotEquals(dark.background, light.background)
        assertNotEquals(dark.onSurface, light.onSurface)
        assertNotEquals(dark.surfaceContainer, light.surfaceContainer)
    }

    @Test
    fun oldSettingsRetainPersonalThemeDefaultsAndSavedPalettes() {
        val existing = CustomTheme(name = "Saved Colorway", primaryColorArgb = blue)
        val settings = Settings(customThemes = listOf(existing), materialYouSourceColor = blue)
        assertEquals(blue, settings.materialYouSourceColor)
        assertNull(settings.personalThemeAccentColor)
        assertEquals(existing.id, settings.customThemes.single().id)
        val wallpaperMode = settings.copy(materialYouSourceColor = null, personalLastBackgroundColor = blue)
        val restored = wallpaperMode.copy(materialYouSourceColor = wallpaperMode.personalLastBackgroundColor)
        assertEquals(blue, restored.materialYouSourceColor)
    }
}
