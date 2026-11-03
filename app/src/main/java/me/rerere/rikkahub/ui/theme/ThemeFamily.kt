package me.rerere.rikkahub.ui.theme

import androidx.compose.runtime.Composable

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

private fun family(
    id: String,
    name: @Composable () -> Unit,
    variations: List<ThemeVariation>,
    accents: List<ThemeAccent>,
) = ThemeFamily(id, name, variations, accents)

val ThemeFamilies: List<ThemeFamily> by lazy {
    listOf(
        // Portable counterpart of OpenCode's Meristem styling: quiet layered
        // surfaces and a restrained accent ladder, not a forced global skin.
        // CustomTheme consumes ARGB longs (not Compose packed color values).
        family(
            "meristem",
            { androidx.compose.material3.Text("Meristem") },
            listOf(
                ThemeVariation("slate", 0xFF8199AD, 0xFF87958C, 0xFFBD9B7B),
                ThemeVariation("warm", 0xFFC19A76, 0xFF96908B, 0xFF7D9F9E),
            ),
            listOf(
                "blue" to 0xFF8199AD,
                "teal" to 0xFF79A6A0,
                "green" to 0xFF8EA886,
                "yellow" to 0xFFC7AD76,
                "peach" to 0xFFC99A82,
                "red" to 0xFFBF7F7E,
                "mauve" to 0xFFAC91B2,
                "pink" to 0xFFC49AAA,
            ).map { ThemeAccent(it.first, it.second) },
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
