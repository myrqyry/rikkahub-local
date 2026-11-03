package dev.neumorphic

import androidx.compose.ui.graphics.Color
import dev.neumorphic.components.selectedSegmentSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeumorphicLightingPolishTest {
    @Test fun zeroDepthDoesNotDrawAShadowIntoTheSurface() {
        assertEquals(0f, insetStrokeWidthPx(depthPx = 0f, shortestSidePx = 50f), 0f)
        assertEquals(0f, insetStrokeWidthPx(depthPx = 4f, shortestSidePx = 0f), 0f)
        assertEquals(0f, insetStrokeWidthPx(depthPx = -2f, shortestSidePx = 50f), 0f)
    }

    @Test fun insetShadowStaysAtTheEdgesEvenForTinyControls() {
        val sizes = listOf(8f, 20f, 40f, 200f)
        for (shortSide in sizes) {
            val band = insetStrokeWidthPx(depthPx = 30f, shortestSidePx = shortSide)
            assertTrue("stroke wider than its permitted edge band", band <= shortSide * 0.32f)
            assertTrue("center must remain unobscured", shortSide - band > 0f)
        }
        assertEquals(0f, insetStrokeWidthPx(Float.NaN, 50f), 0f)
    }

    @Test fun activeSegmentIsSubtlyTintedWithoutBeingAnOpaqueAccentBlock() {
        val colors = createNeumorphicPaletteWithAccent(
            isDark = false,
            accentColor = Color(0xFF5263A7),
            preset = AccentColorPreset.Graphite,
        )
        val selected = selectedSegmentSurface(colors)
        assertFalse(selected == colors.surface)
        assertFalse(selected == colors.accentPrimary)
        assertEquals(1f, selected.alpha, 0.0001f)
    }

    @Test fun selectedSegmentIsStillDistinctInDarkTheme() {
        val colors = createNeumorphicPalette(isDark = true, preset = AccentColorPreset.Graphite)
        val selected = selectedSegmentSurface(colors)
        assertFalse(selected == colors.surface)
        assertFalse(selected == colors.accentPrimary)
    }
}
