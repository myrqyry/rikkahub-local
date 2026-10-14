package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.neumorphic.AccentColorPreset
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.createNeumorphicPaletteWithAccent

/**
 * Adapt the independently developed neumorphic renderer to *existing* Material
 * colors. It provides tactile geometry only; it does not install a third theme.
 */
@Composable
fun rememberRikkaNeumorphicColors(): NeumorphicColors {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkMode.current
    return remember(scheme, dark) {
        val colors = createNeumorphicPaletteWithAccent(
            isDark = dark,
            accentColor = scheme.primary,
            preset = AccentColorPreset.Graphite,
            depthIntensity = 0.75f,
        )
        colors.copy(
            background = scheme.background,
            surface = scheme.surfaceContainerLow,
            lightShadow = if (dark) scheme.onSurface.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.85f),
            darkShadow = if (dark) Color.Black.copy(alpha = 0.55f) else scheme.onSurface.copy(alpha = 0.22f),
            innerLightShadow = if (dark) scheme.onSurface.copy(alpha = 0.11f) else Color.White.copy(alpha = 0.9f),
            innerDarkShadow = if (dark) Color.Black.copy(alpha = 0.5f) else scheme.onSurface.copy(alpha = 0.23f),
            textPrimary = scheme.onSurface,
            textSecondary = scheme.onSurfaceVariant,
            borderSubtle = scheme.outlineVariant,
            accentPrimary = scheme.primary,
            accentOnPrimary = scheme.onPrimary,
        )
    }
}
