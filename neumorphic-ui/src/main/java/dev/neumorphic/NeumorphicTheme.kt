package dev.neumorphic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Curated accent palettes for the neumorphic system.
 *
 * Each preset carries light/dark color pairs, gradient stops, shadow tints and
 * a tuned [optimalDepthMultiplier]. Keep the list curated — these presets are
 * part of the design personality.
 */
enum class AccentColorPreset(
    val displayName: String,
    val lightPrimary: Color,
    val lightSecondary: Color,
    val lightGradientStart: Color,
    val lightGradientEnd: Color,
    val lightOnPrimary: Color,
    val lightShadowTint: Color,
    val lightDarkShadowTint: Color,
    val darkPrimary: Color,
    val darkSecondary: Color,
    val darkGradientStart: Color,
    val darkGradientEnd: Color,
    val darkOnPrimary: Color,
    val darkSpecularHighlight: Color,
    val darkShadowTint: Color,
    /**
     * Per-preset tuning for depth intensity. Warm/high-saturation accents (rose)
     * need slightly more depth to keep highlights visible; cool/muted ones need less.
     * Effective depth = userIntensity * this. Clamped in [createNeumorphicPalette].
     * Keep ~0.95..1.08; larger values wash out light theme and blow out dark shadows.
     */
    val optimalDepthMultiplier: Float
) {
    OceanBlue(
        displayName = "Ocean Blue",
        lightPrimary = Color(0xFF0061A4),
        lightSecondary = Color(0xFF535F70),
        lightGradientStart = Color(0xFF0061A4),
        lightGradientEnd = Color(0xFF2E75D3),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightShadowTint = Color(0xFFFFFFFF),
        lightDarkShadowTint = Color(0xFFB4C2D6),
        darkPrimary = Color(0xFFD1E4FF),
        darkSecondary = Color(0xFFA8C7F0),
        darkGradientStart = Color(0xFFD1E4FF),
        darkGradientEnd = Color(0xFFA8C7F0),
        darkOnPrimary = Color(0xFF003258),
        darkSpecularHighlight = Color(0x3380B5FF),
        darkShadowTint = Color(0x66000000),
        optimalDepthMultiplier = 1.0f
    ),
    EmeraldJade(
        displayName = "Emerald Jade",
        lightPrimary = Color(0xFF006D44),
        lightSecondary = Color(0xFF4E6354),
        lightGradientStart = Color(0xFF006D44),
        lightGradientEnd = Color(0xFF00A36C),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightShadowTint = Color(0xFFF2FFF6),
        lightDarkShadowTint = Color(0xFFAEC4B5),
        darkPrimary = Color(0xFF76DB9E),
        darkSecondary = Color(0xFFA2D3B4),
        darkGradientStart = Color(0xFF76DB9E),
        darkGradientEnd = Color(0xFF20B26E),
        darkOnPrimary = Color(0xFF003920),
        darkSpecularHighlight = Color(0x3576DB9E),
        darkShadowTint = Color(0x66000803),
        optimalDepthMultiplier = 1.05f
    ),
    CyberViolet(
        displayName = "Cyber Violet",
        lightPrimary = Color(0xFF6B4EA2),
        lightSecondary = Color(0xFF625B71),
        lightGradientStart = Color(0xFF6B4EA2),
        lightGradientEnd = Color(0xFF9C47D3),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightShadowTint = Color(0xFFFCF7FF),
        lightDarkShadowTint = Color(0xFFC0B8D0),
        darkPrimary = Color(0xFFD0BCFF),
        darkSecondary = Color(0xFFCCC2DC),
        darkGradientStart = Color(0xFFD0BCFF),
        darkGradientEnd = Color(0xFFB388FF),
        darkOnPrimary = Color(0xFF381E72),
        darkSpecularHighlight = Color(0x35D0BCFF),
        darkShadowTint = Color(0x6606000C),
        optimalDepthMultiplier = 1.0f
    ),
    SunsetAmber(
        displayName = "Sunset Amber",
        lightPrimary = Color(0xFFB85D00),
        lightSecondary = Color(0xFF715B4C),
        lightGradientStart = Color(0xFFB85D00),
        lightGradientEnd = Color(0xFFE67E22),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightShadowTint = Color(0xFFFFFDF8),
        lightDarkShadowTint = Color(0xFFCCBCAC),
        darkPrimary = Color(0xFFFFB778),
        darkSecondary = Color(0xFFDDC2B1),
        darkGradientStart = Color(0xFFFFB778),
        darkGradientEnd = Color(0xFFFFA043),
        darkOnPrimary = Color(0xFF4D2600),
        darkSpecularHighlight = Color(0x35FFB778),
        darkShadowTint = Color(0x660A0400),
        optimalDepthMultiplier = 0.95f
    ),
    CrimsonRose(
        displayName = "Crimson Rose",
        lightPrimary = Color(0xFFBA1A1A),
        lightSecondary = Color(0xFF775653),
        lightGradientStart = Color(0xFFBA1A1A),
        lightGradientEnd = Color(0xFFE53935),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightShadowTint = Color(0xFFFFF9F9),
        lightDarkShadowTint = Color(0xFFCCB6B4),
        darkPrimary = Color(0xFFFFB4AB),
        darkSecondary = Color(0xFFE7BDB8),
        darkGradientStart = Color(0xFFFFB4AB),
        darkGradientEnd = Color(0xFFFF6B81),
        darkOnPrimary = Color(0xFF690005),
        darkSpecularHighlight = Color(0x35FFB4AB),
        darkShadowTint = Color(0x660A0002),
        optimalDepthMultiplier = 1.08f
    ),
    ElectricCyan(
        displayName = "Electric Cyan",
        lightPrimary = Color(0xFF00687A),
        lightSecondary = Color(0xFF4A6267),
        lightGradientStart = Color(0xFF00687A),
        lightGradientEnd = Color(0xFF0097A7),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightShadowTint = Color(0xFFF0FCFF),
        lightDarkShadowTint = Color(0xFFA8C4CA),
        darkPrimary = Color(0xFF4DD8EC),
        darkSecondary = Color(0xFFB1CBD0),
        darkGradientStart = Color(0xFF4DD8EC),
        darkGradientEnd = Color(0xFF00E5FF),
        darkOnPrimary = Color(0xFF003640),
        darkSpecularHighlight = Color(0x354DD8EC),
        darkShadowTint = Color(0x6600080B),
        optimalDepthMultiplier = 1.02f
    ),
    /** Monochrome — neutral graphite, no hue. Keeps the neumorphic depth without accent color. */
    Graphite(
        displayName = "Graphite",
        lightPrimary = Color(0xFF2F3133),
        lightSecondary = Color(0xFF5A5E62),
        lightGradientStart = Color(0xFF3A3D40),
        lightGradientEnd = Color(0xFF5A5E62),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightShadowTint = Color(0xFFFFFFFF),
        lightDarkShadowTint = Color(0xFFBFC6CE),
        darkPrimary = Color(0xFFE2E2E6),
        darkSecondary = Color(0xFFC4C7C5),
        darkGradientStart = Color(0xFFE2E2E6),
        darkGradientEnd = Color(0xFFC4C7C5),
        darkOnPrimary = Color(0xFF1A1C1E),
        darkSpecularHighlight = Color(0x33E2E2E6),
        darkShadowTint = Color(0x66000000),
        optimalDepthMultiplier = 1.0f
    )
}

/**
 * Full palette for the neumorphic system. Use [LocalNeumorphicColors] to read
 * the current palette inside composition, or create one via [createNeumorphicPalette].
 *
 * @property depthIntensityMultiplier effective depth after accent tuning and clamping.
 */
@Immutable
data class NeumorphicColors(
    val background: Color,
    val surface: Color,
    val lightShadow: Color,
    val darkShadow: Color,
    val innerLightShadow: Color,
    val innerDarkShadow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentOnPrimary: Color,
    val borderSubtle: Color,
    val accentGradientStart: Color,
    val accentGradientEnd: Color,
    val isDark: Boolean,
    val accentPreset: AccentColorPreset = AccentColorPreset.OceanBlue,
    val depthIntensityMultiplier: Float = 1.0f
) {
    /** Gradient for accent-filled surfaces. */
    val accentGradient: Brush
        get() = Brush.linearGradient(listOf(accentGradientStart, accentGradientEnd))

    /** Subtle convex gradient for raised surfaces. */
    val convexSurfaceGradient: Brush
        get() = Brush.linearGradient(
            listOf(
                surface.copy(alpha = 1f),
                if (isDark) surface.copy(red = (surface.red * 0.9f).coerceIn(0f, 1f))
                else surface.copy(red = (surface.red * 0.96f).coerceIn(0f, 1f))
            )
        )
}


/**
 * Surface palette — background, surface, shadows, border and text.
 * Derived from [NeumorphicColors] via [NeumorphicColors.surfacePalette].
 * Useful when you need only the surface part without the accent.
 */
@Immutable
data class NeumorphicSurfaceColors(
    val background: Color,
    val surface: Color,
    val lightShadow: Color,
    val darkShadow: Color,
    val innerLightShadow: Color,
    val innerDarkShadow: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val isDark: Boolean
)

/**
 * Accent palette — the hue-carrying part. Immutable per preset.
 * Surface-agnostic: same accent works in light and dark via different tints.
 */
@Immutable
data class NeumorphicAccentColors(
    val primary: Color,
    val secondary: Color,
    val onPrimary: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val preset: AccentColorPreset
)

/** Extract surface-only palette. */
val NeumorphicColors.surfacePalette: NeumorphicSurfaceColors
    get() = NeumorphicSurfaceColors(background, surface, lightShadow, darkShadow, innerLightShadow, innerDarkShadow, borderSubtle, textPrimary, textSecondary, isDark)

/** Extract accent-only palette. */
val NeumorphicColors.accentPalette: NeumorphicAccentColors
    get() = NeumorphicAccentColors(accentPrimary, accentSecondary, accentOnPrimary, accentGradientStart, accentGradientEnd, accentPreset)

/**
 * Builds a tuned palette for the given mode, accent and user depth.
 *
 * Depth is modulated by [AccentColorPreset.optimalDepthMultiplier] and clamped to 0.5..2.0
 * to prevent ugly clipping or extreme offsets. Alpha for specular/umbra follows the
 * effective depth so light themes don't wash out and dark themes keep contrast.
 */
fun createNeumorphicPalette(
    isDark: Boolean,
    preset: AccentColorPreset = AccentColorPreset.OceanBlue,
    depthIntensity: Float = 1.0f
): NeumorphicColors {
    val effectiveDepth = (depthIntensity * preset.optimalDepthMultiplier).coerceIn(0.5f, 2.0f)

    return if (isDark) {
        val specularAlpha = (0.24f * effectiveDepth).coerceIn(0.08f, 0.45f)
        val umbraAlpha = (0.75f * effectiveDepth).coerceIn(0.4f, 0.95f)

        NeumorphicColors(
            background = Color(0xFF1A1C1E),
            surface = Color(0xFF1A1C1E),
            lightShadow = preset.darkSpecularHighlight.copy(alpha = specularAlpha),
            darkShadow = preset.darkShadowTint.copy(alpha = umbraAlpha),
            innerLightShadow = preset.darkSpecularHighlight.copy(alpha = (specularAlpha * 0.9f).coerceIn(0.06f, 0.35f)),
            innerDarkShadow = Color(0x80000000),
            textPrimary = Color(0xFFE2E2E6),
            textSecondary = Color(0xFFC4C7C5),
            accentPrimary = preset.darkPrimary,
            accentSecondary = preset.darkSecondary,
            accentOnPrimary = preset.darkOnPrimary,
            borderSubtle = Color(0xFF2A2C2E),
            accentGradientStart = preset.darkGradientStart,
            accentGradientEnd = preset.darkGradientEnd,
            isDark = true,
            accentPreset = preset,
            depthIntensityMultiplier = effectiveDepth
        )
    } else {
        val lightAlpha = (0.95f * effectiveDepth).coerceIn(0.5f, 1.0f)
        val darkAlpha = (0.85f * effectiveDepth).coerceIn(0.4f, 1.0f)

        NeumorphicColors(
            background = Color(0xFFE9EEF5),
            surface = Color(0xFFE9EEF5),
            lightShadow = preset.lightShadowTint.copy(alpha = lightAlpha),
            darkShadow = preset.lightDarkShadowTint.copy(alpha = darkAlpha),
            innerLightShadow = Color(0xCCFFFFFF),
            innerDarkShadow = preset.lightDarkShadowTint.copy(alpha = (darkAlpha * 0.45f).coerceIn(0.15f, 0.6f)),
            textPrimary = Color(0xFF1A1C1E),
            textSecondary = Color(0xFF44474E),
            accentPrimary = preset.lightPrimary,
            accentSecondary = preset.lightSecondary,
            accentOnPrimary = preset.lightOnPrimary,
            borderSubtle = Color(0xFFE1E2E8),
            accentGradientStart = preset.lightGradientStart,
            accentGradientEnd = preset.lightGradientEnd,
            isDark = false,
            accentPreset = preset,
            depthIntensityMultiplier = effectiveDepth
        )
    }
}

internal val LightNeumorphicColors = createNeumorphicPalette(isDark = false, preset = AccentColorPreset.OceanBlue)
internal val DarkNeumorphicColors = createNeumorphicPalette(isDark = true, preset = AccentColorPreset.OceanBlue)
/**
 * Create a palette from a custom or system-derived accent color.
 *
 * Keeps the neumorphic surface (light/dark shadows, background) from the chosen [preset] so
 * depth stays tuned, but overrides the accent ramp with [accentColor]. This is the
 * opt-in path for Material dynamic color / system palette without making dynamic color
 * mandatory: callers that have a seed color (e.g. from wallpaper) can pass it here;
 * default presets remain curated when not used.
 *
 * Contrast is preserved because [accentOnPrimary] is derived via luminance vs. the
 * generated tonal palette; if you need WCAG guarantees, check `accentContrast` via
 * `isLightColor(accentColor)`.
 *
 * Prefer curated [AccentColorPreset] when you want personality; use this escape hatch
 * for user-configurable or dynamic-color builds.
 */
fun createNeumorphicPaletteWithAccent(
    isDark: Boolean,
    accentColor: Color,
    preset: AccentColorPreset = AccentColorPreset.OceanBlue,
    depthIntensity: Float = 1.0f
): NeumorphicColors {
    val base = createNeumorphicPalette(isDark = isDark, preset = preset, depthIntensity = depthIntensity)
    val onAccent = if (isLightColor(accentColor)) Color(0xFF1A1C1E) else Color.White
    // Derive secondary as desaturated accent, gradient as accent ± 12% lightness (cheap, keeps hue)
    val secondary = accentColor.copy(alpha = 1f, red = (accentColor.red * 0.78f).coerceIn(0f,1f), green = (accentColor.green * 0.80f).coerceIn(0f,1f), blue = (accentColor.blue * 0.82f).coerceIn(0f,1f))
    val gradStart = accentColor
    val gradEnd = accentColor.copy(red = (accentColor.red * 1.12f).coerceIn(0f,1f), green = (accentColor.green * 1.10f).coerceIn(0f,1f), blue = (accentColor.blue * 1.08f).coerceIn(0f,1f))
    return base.copy(
        accentPrimary = accentColor,
        accentSecondary = secondary,
        accentOnPrimary = onAccent,
        accentGradientStart = gradStart,
        accentGradientEnd = gradEnd,
        accentPreset = preset
    )
}

/** Simple luminance check for accent-on-contrast. Internal — prefer WCAG 4.5:1 checks in tests. */
internal fun isLightColor(c: Color): Boolean =
    (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) > 0.55f


/**
 * Geometry for neumorphic surfaces.
 *
 * Prefer the semantic tokens in [NeumorphicShape.Companion] over raw radii.
 */
sealed class NeumorphicShape {
    /** Rounded rectangle with custom radius. */
    data class Rounded(val cornerRadius: Dp = 16.dp) : NeumorphicShape()
    /** Perfect circle (clipped to smallest side). */
    object Circle : NeumorphicShape()
    /** Pill (capsule) — corner radius is half the height. */
    object Pill : NeumorphicShape()

    companion object {
        /** 12.dp — chips, small controls. */
        val Small: NeumorphicShape = Rounded(12.dp)
        /** 16.dp — default for cards, buttons. */
        val Medium: NeumorphicShape = Rounded(16.dp)
        /** 24.dp — large cards, sheets. */
        val Large: NeumorphicShape = Rounded(24.dp)
        /** Pill shape token. */
        // Getter, not val: eager `= Pill` poisons to null if Pill is first-touched
        // (object init re-enters this clinit and reads its own INSTANCE early).
        val PillShape: NeumorphicShape get() = Pill
        /** Circular shape token. */
        // Getter for the same reason as PillShape: `= Circle` reads null when
        // Circle is the first-touched member (crashed Studio's shape row).
        val Circular: NeumorphicShape get() = Circle
    }
}

/**
 * Semantic depth levels. Provides a constrained, reviewable scale instead of
 * arbitrary Dp values scattered across components.
 */
enum class NeumorphicElevation(val dp: Dp) {
    /** 2.dp — subtle inset/dividers. */
    Subtle(2.dp),
    /** 6.dp — default for buttons/cards. */
    Normal(6.dp),
    /** 10.dp — prominent sheets/dials. */
    Prominent(10.dp)
}

/**
 * CompositionLocal for the current [NeumorphicColors].
 * Read via `LocalNeumorphicColors.current` or wrap content in [NeumorphicTheme].
 */
val LocalNeumorphicColors = staticCompositionLocalOf { LightNeumorphicColors }

/**
 * Neumorphic theme — small public API by design.
 *
 * Wraps content with animated [NeumorphicColors] derived from [darkTheme],
 * [accentPreset] and [depthIntensity]. Depth is clamped and accent-tuned inside
 * [createNeumorphicPalette] so callers don't deal with extremes.
 *
 * @param darkTheme whether to use dark palette (defaults to system).
 * @param accentPreset curated accent; keep the list small to preserve identity.
 * @param depthIntensity global depth 0.5..2.0 (clamped). Prefer 1.0 and adjust per-component via [NeumorphicElevation] if needed.
 */
@Composable
fun NeumorphicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentPreset: AccentColorPreset = AccentColorPreset.OceanBlue,
    depthIntensity: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val targetColors = createNeumorphicPalette(
        isDark = darkTheme,
        preset = accentPreset,
        depthIntensity = depthIntensity
    )

    val background = animateColorAsState(targetColors.background, tween(300), label = "bg").value
    val surface = animateColorAsState(targetColors.surface, tween(300), label = "surface").value
    val lightShadow = animateColorAsState(targetColors.lightShadow, tween(300), label = "lightShadow").value
    val darkShadow = animateColorAsState(targetColors.darkShadow, tween(300), label = "darkShadow").value
    val innerLightShadow = animateColorAsState(targetColors.innerLightShadow, tween(300), label = "innerLight").value
    val innerDarkShadow = animateColorAsState(targetColors.innerDarkShadow, tween(300), label = "innerDark").value
    val textPrimary = animateColorAsState(targetColors.textPrimary, tween(300), label = "textPrimary").value
    val textSecondary = animateColorAsState(targetColors.textSecondary, tween(300), label = "textSecondary").value
    val accentPrimary = animateColorAsState(targetColors.accentPrimary, tween(300), label = "accentPrimary").value
    val accentSecondary = animateColorAsState(targetColors.accentSecondary, tween(300), label = "accentSecondary").value
    val accentOnPrimary = animateColorAsState(targetColors.accentOnPrimary, tween(300), label = "accOnPrim").value
    val borderSubtle = animateColorAsState(targetColors.borderSubtle, tween(300), label = "borderSubtle").value
    val accentGradientStart = animateColorAsState(targetColors.accentGradientStart, tween(300), label = "accGrad1").value
    val accentGradientEnd = animateColorAsState(targetColors.accentGradientEnd, tween(300), label = "accGrad2").value

    val animatedColors = NeumorphicColors(
        background = background,
        surface = surface,
        lightShadow = lightShadow,
        darkShadow = darkShadow,
        innerLightShadow = innerLightShadow,
        innerDarkShadow = innerDarkShadow,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        accentPrimary = accentPrimary,
        accentSecondary = accentSecondary,
        accentOnPrimary = accentOnPrimary,
        borderSubtle = borderSubtle,
        accentGradientStart = accentGradientStart,
        accentGradientEnd = accentGradientEnd,
        isDark = darkTheme,
        accentPreset = accentPreset,
        depthIntensityMultiplier = targetColors.depthIntensityMultiplier
    )

    CompositionLocalProvider(LocalNeumorphicColors provides animatedColors) {
        content()
    }
}
