package me.rerere.rikkahub.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberCurrentColorMode
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }

private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

// Selected only for the Meristem theme; other themes keep their own expressive shapes.
// Deliberately restrained shared geometry for both Material You and Meristem.
// The former expressive defaults produced oversized pill/sheet corners across screens.
private val WorkspaceShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
    extraExtraLarge = RoundedCornerShape(30.dp),
)

const val MERISTEM_THEME_ID = "meristem"
const val MATERIAL_YOU_FALLBACK_COLOR: Long = 0xFF8199AD

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun RikkahubTheme(
    colorMode: ColorMode = rememberCurrentColorMode(),
    content: @Composable () -> Unit
) {
    val settings by rememberUserSettingsState()

    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val amoledDarkMode by rememberAmoledDarkMode()

    val context = LocalContext.current
    val wallpaperScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        CustomTheme(primaryColorArgb = MATERIAL_YOU_FALLBACK_COLOR).generateColorScheme(darkTheme)
    }
    val colorScheme = when {
        settings.dynamicColor -> personalPalette(
            dark = darkTheme,
            wallpaper = wallpaperScheme,
            backgroundSeed = settings.materialYouSourceColor,
            accentOverride = settings.personalThemeAccentColor,
        )
        // Retain existing user-defined/downloaded themes exactly as saved.
        settings.themeId != MERISTEM_THEME_ID -> {
            findThemeById(settings.themeId, settings.customThemes)
                ?.getColorScheme(dark = darkTheme)
                ?: personalPalette(
                    darkTheme, wallpaperScheme, settings.materialYouSourceColor,
                    settings.personalThemeAccentColor,
                )
        }
        // Previously selected Meristem stays readable without rewriting prefs;
        // the Appearance page can bring it into the single personal theme.
        else -> findThemeFamily(settings.themeId)?.colorScheme(
            variationId = settings.themeVariation,
            accentId = settings.themeAccent,
            dark = darkTheme,
        ) ?: personalPalette(
            darkTheme, wallpaperScheme, settings.materialYouSourceColor,
            settings.personalThemeAccentColor,
        )
    }
    val colorSchemeConverted = remember(darkTheme, amoledDarkMode, colorScheme) {
        if (darkTheme && amoledDarkMode) {
            colorScheme.copy(
                background = AMOLED_DARK_BACKGROUND,
                surface = AMOLED_DARK_BACKGROUND,
            )
        } else {
            colorScheme
        }
    }
    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors

    // 更新状态栏图标颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkMode provides darkTheme,
        LocalExtendColors provides extendColors,
        LocalOverscrollFactory provides null
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorSchemeConverted,
            typography = Typography,
            content = content,
            motionScheme = MotionScheme.expressive(),
            shapes = WorkspaceShapes
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
