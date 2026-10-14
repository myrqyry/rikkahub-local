package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import hct.Hct
import palettes.TonalPalette

/**
 * One visual design; the color source is independent of component geometry.
 *
 * Wallpaper mode retains the actual Android dynamic scheme. For a manually
 * chosen background hue, use a more chromatic tonal *neutral* ladder so the
 * whole interface inherits its character instead of turning nearly gray.
 * Accent override replaces only chromatic roles; background/surfaces survive.
 */
fun personalPalette(
    dark: Boolean,
    wallpaper: ColorScheme,
    backgroundSeed: Long?,
    accentOverride: Long?,
): ColorScheme {
    val base = if (backgroundSeed == null) wallpaper else {
        val generated = CustomTheme(primaryColorArgb = backgroundSeed).generateColorScheme(dark)
        val source = Hct.fromInt(backgroundSeed.toInt())
        val surfaces = TonalPalette.fromHueAndChroma(source.hue, 20.0)
        fun at(tone: Int) = Color(surfaces.tone(tone))
        if (dark) {
            generated.copy(
                background = at(6),
                surface = at(8),
                surfaceDim = at(6),
                surfaceBright = at(24),
                surfaceContainerLowest = at(4),
                surfaceContainerLow = at(10),
                surfaceContainer = at(12),
                surfaceContainerHigh = at(17),
                surfaceContainerHighest = at(22),
                surfaceVariant = at(28),
            )
        } else {
            generated.copy(
                background = at(98),
                surface = at(98),
                surfaceDim = at(87),
                surfaceBright = at(98),
                surfaceContainerLowest = at(100),
                surfaceContainerLow = at(96),
                surfaceContainer = at(94),
                surfaceContainerHigh = at(92),
                surfaceContainerHighest = at(90),
                surfaceVariant = at(90),
            )
        }
    }

    if (accentOverride == null) return base
    val accent = CustomTheme(primaryColorArgb = accentOverride).generateColorScheme(dark)
    return base.copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        inversePrimary = accent.inversePrimary,
        secondary = accent.secondary,
        onSecondary = accent.onSecondary,
        secondaryContainer = accent.secondaryContainer,
        onSecondaryContainer = accent.onSecondaryContainer,
        tertiary = accent.tertiary,
        onTertiary = accent.onTertiary,
        tertiaryContainer = accent.tertiaryContainer,
        onTertiaryContainer = accent.onTertiaryContainer,
        surfaceTint = accent.primary,
    )
}
