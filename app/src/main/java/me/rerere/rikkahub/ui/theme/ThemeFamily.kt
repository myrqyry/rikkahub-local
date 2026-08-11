package me.rerere.rikkahub.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.R

data class ThemeVariation(
    val id: String,
    val primarySeed: Long,
    val secondarySeed: Long,
    val tertiarySeed: Long,
)

data class ThemeAccent(
    val id: String,
    val seed: Long,
)

data class ThemeFamily(
    val id: String,
    val name: @Composable () -> Unit,
    val variations: List<ThemeVariation>,
    val accents: List<ThemeAccent>,
) {
    val defaultVariation: ThemeVariation get() = variations.first()
    val defaultAccent: ThemeAccent get() = accents.first()

    fun colorScheme(variationId: String, accentId: String, dark: Boolean) : androidx.compose.material3.ColorScheme {
        val variation = variations.find { it.id == variationId } ?: defaultVariation
        val accent = accents.find { it.id == accentId } ?: defaultAccent
        return CustomTheme(
            primaryColorArgb = accent.seed,
            secondaryColorArgb = variation.secondarySeed,
            tertiaryColorArgb = variation.tertiarySeed,
        ).generateColorScheme(dark)
    }

    fun defaultPreset(): PresetTheme = PresetTheme(
        id = id,
        name = name,
        standardLight = colorScheme(defaultVariation.id, defaultAccent.id, dark = false),
        standardDark = colorScheme(defaultVariation.id, defaultAccent.id, dark = true),
    )
}

private fun seed(hex: Long): Long = Color(hex).value.toLong()

private fun family(
    id: String,
    name: @Composable () -> Unit,
    variations: List<ThemeVariation>,
    accents: List<ThemeAccent>,
) = ThemeFamily(id, name, variations, accents)

val ThemeFamilies: List<ThemeFamily> by lazy {
    listOf(
        family(
            "dracula",
            { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.theme_name_dracula)) },
            listOf(
                ThemeVariation("default", seed(0xFFBD93F9), seed(0xFF6272A4), seed(0xFFFF79C6)),
                ThemeVariation("soft", seed(0xFFBFA2DB), seed(0xFF7082A8), seed(0xFFE69AB7)),
                ThemeVariation("high_contrast", seed(0xFFD6ACFF), seed(0xFF8BE9FD), seed(0xFFFF5555)),
            ),
            listOf("purple" to 0xFFBD93F9, "pink" to 0xFFFF79C6, "cyan" to 0xFF8BE9FD, "green" to 0xFF50FA7B, "orange" to 0xFFFFB86C, "yellow" to 0xFFF1FA8C).map { ThemeAccent(it.first, seed(it.second)) },
        ),
        family(
            "catppuccin",
            { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.theme_name_catppuccin)) },
            listOf(
                ThemeVariation("mocha", seed(0xFFCBA6F7), seed(0xFF89B4FA), seed(0xFFF5C2E7)),
                ThemeVariation("macchiato", seed(0xFFC6A0F6), seed(0xFF8AADF4), seed(0xFFF5BDE6)),
                ThemeVariation("frappe", seed(0xFFCA9EE6), seed(0xFF8CAAEE), seed(0xFFF4B8E4)),
            ),
            listOf("lavender" to 0xFFB4BEFE, "blue" to 0xFF89B4FA, "sapphire" to 0xFF74C7EC, "mauve" to 0xFFCBA6F7, "rosewater" to 0xFFF5E0E6, "flamingo" to 0xFFF2CDCD, "pink" to 0xFFF5C2E7, "red" to 0xFFF38BA8, "maroon" to 0xFFEBA0AC, "peach" to 0xFFFAB387, "yellow" to 0xFFF9E2AF, "green" to 0xFFA6E3A1, "teal" to 0xFF94E2D5, "sky" to 0xFF89DCEB).map { ThemeAccent(it.first, seed(it.second)) },
        ),
        family(
            "rose_pine",
            { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.theme_name_rose_pine)) },
            listOf(
                ThemeVariation("default", seed(0xFFC4A7E7), seed(0xFF9CCFD8), seed(0xFFEBBCBA)),
                ThemeVariation("moon", seed(0xFFC4A7E7), seed(0xFF9CCFD8), seed(0xFFF6C177)),
            ),
            listOf("iris" to 0xFFC4A7E7, "rose" to 0xFFEBBCBA, "gold" to 0xFFF6C177, "pine" to 0xFF31748F, "foam" to 0xFF9CCFD8, "love" to 0xFFEB6F92).map { ThemeAccent(it.first, seed(it.second)) },
        ),
        family(
            "tokyo_night",
            { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.theme_name_tokyo_night)) },
            listOf(
                ThemeVariation("night", seed(0xFF7AA2F7), seed(0xFFBB9AF7), seed(0xFF7DCFFF)),
                ThemeVariation("storm", seed(0xFF7AA2F7), seed(0xFF9D7CD8), seed(0xFF2AC3DE)),
            ),
            listOf("purple" to 0xFFBB9AF7, "blue" to 0xFF7AA2F7, "cyan" to 0xFF7DCFFF, "green" to 0xFF9ECE6A, "orange" to 0xFFFF9E64, "red" to 0xFFF7768E).map { ThemeAccent(it.first, seed(it.second)) },
        ),
        family(
            "gruvbox_dark",
            { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.theme_name_gruvbox_dark)) },
            listOf(
                ThemeVariation("medium", seed(0xFFD79921), seed(0xFF689D6A), seed(0xFFB16286)),
                ThemeVariation("hard", seed(0xFFFABD2F), seed(0xFF8EC07C), seed(0xFFD3869B)),
                ThemeVariation("soft", seed(0xFFD8A657), seed(0xFFA9B665), seed(0xFFD3869B)),
            ),
            listOf("yellow" to 0xFFD79921, "orange" to 0xFFD65D0E, "red" to 0xFFCC241D, "green" to 0xFF98971A, "aqua" to 0xFF689D6A, "blue" to 0xFF458588, "purple" to 0xFFB16286).map { ThemeAccent(it.first, seed(it.second)) },
        ),
    )
}

fun findThemeFamily(id: String): ThemeFamily? = ThemeFamilies.find { it.id == id }

fun nextThemeVariation(family: ThemeFamily, current: String): String {
    val index = family.variations.indexOfFirst { it.id == current }.let { if (it < 0) 0 else it }
    return family.variations[(index + 1) % family.variations.size].id
}

fun nextThemeAccent(family: ThemeFamily, current: String): String {
    val index = family.accents.indexOfFirst { it.id == current }.let { if (it < 0) 0 else it }
    return family.accents[(index + 1) % family.accents.size].id
}
