package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.ByteArrayOutputStream

/**
 * A single light OR dark Colorway palette. The opposite mode uses generated
 * Material tonal colors: don't silently reuse a dark palette in a light UI.
 */
@Serializable
data class ImportedThemePalette(
    val dark: Boolean,
    val roles: Map<String, Long>,
) {
    fun applyTo(base: ColorScheme): ColorScheme {
        fun get(key: String, fallback: Color) = roles[key]?.let { Color(it.toInt()) } ?: fallback
        val background = get("--bg_base", base.background)
        val surface = get("--bg_window", background)
        val raised = get("--bg_dock", base.surfaceContainer)
        val preview = get("--bg_preview", base.surfaceContainerLow)
        val primary = get("--primary", base.primary)
        val text = legible(get("--text", base.onSurface), surface)
        val primaryText = legible(get("--text_inverse", base.onPrimary), primary)
        val muted = legible(get("--text_muted", base.onSurfaceVariant), raised)
        val border = get("--border_color", base.outlineVariant)
        return base.copy(
            background = background,
            surface = surface,
            surfaceDim = get("--bg_window", base.surfaceDim),
            surfaceBright = get("--bg_hover", base.surfaceBright),
            surfaceContainerLowest = background,
            surfaceContainerLow = preview,
            surfaceContainer = raised,
            surfaceContainerHigh = get("--bg_hover", raised),
            surfaceContainerHighest = get("--input_bg", raised),
            onBackground = legible(text, background),
            onSurface = text,
            onSurfaceVariant = muted,
            surfaceVariant = raised,
            primary = primary,
            onPrimary = primaryText,
            primaryContainer = get("--button_bg", base.primaryContainer),
            onPrimaryContainer = legible(text, get("--button_bg", base.primaryContainer)),
            secondary = get("--primary_light", base.secondary),
            tertiary = get("--primary_dark", base.tertiary),
            error = get("--danger", base.error),
            outline = get("--input_border", border),
            outlineVariant = border,
        )
    }
}

private fun luminance(c: Color): Double {
    fun channel(v: Float): Double {
        val x = v.toDouble()
        return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
}

private fun legible(foreground: Color, background: Color): Color {
    fun ratio(a: Double, b: Double) = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    val bg = luminance(background)
    if (ratio(luminance(foreground), bg) >= 4.5) return foreground
    return if (ratio(1.0, bg) >= ratio(0.0, bg)) Color.White else Color.Black
}

/**
 * Accept legacy RikkaHub JSON or Colorway/OBS .ovt text as *data*, never CSS/code.
 * Comments are stripped before reading values (Colorway keeps historical values
 * in annotations that must not accidentally become the live palette).
 */
object ThemeImport {
    private const val MAX_CHARS = 96 * 1024
    private val json = Json { ignoreUnknownKeys = true }
    private val metaBlock = Regex("""@OBSThemeMeta\s*\{([\s\S]*?)\}""")
    private val varsBlock = Regex("""@OBSThemeVars\s*\{([\s\S]*?)\}""")
    private val comments = Regex("""/\*[\s\S]*?\*/""")
    private val metadata = Regex("""(?m)^\s*([a-zA-Z_]+)\s*:\s*['"]([^'"]*)['"]""")
    private val variable = Regex("""(?m)^\s*(--[a-zA-Z_][\w-]*)\s*:\s*([^;\r\n]+);""")
    private val hex = Regex("""^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3})$""")
    private val alias = Regex("""^var\(\s*(--[\w-]+)\s*\)$""")
    private val supported = setOf(
        "--bg_base", "--bg_window", "--bg_preview", "--bg_dock", "--bg_hover",
        "--text", "--text_muted", "--text_inverse", "--primary", "--primary_light",
        "--primary_dark", "--input_bg", "--input_border", "--button_bg",
        "--border_color", "--danger",
    )

    /** Safe for SAF/content resolver input. Never buffer an unbounded file. */
    fun readLimited(input: InputStream): String {
        val buffer = ByteArray(4096)
        val bytes = ByteArrayOutputStream()
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(bytes.size() + n <= MAX_CHARS) { "Theme file must be smaller than 96 KiB." }
            bytes.write(buffer, 0, n)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    fun parse(text: String): CustomTheme {
        require(text.length in 2..MAX_CHARS) { "Theme must contain 2–98304 characters." }
        if (text.trimStart().startsWith("{")) {
            val theme = json.decodeFromString<CustomTheme>(text)
            require(theme.name.isNotBlank() && theme.name.length <= 100) { "Theme name must be 1–100 characters." }
            require((theme.importedPalette?.roles?.size ?: 0) <= 40) { "Too many palette roles." }
            return theme
        }
        val meta = metaBlock.find(text)?.groupValues?.get(1)
            ?.let { metadata.findAll(it).associate { m -> m.groupValues[1] to m.groupValues[2] } }
            ?: throw IllegalArgumentException("Not a Colorway/OBS theme: missing metadata.")
        val name = meta["name"]?.trim() ?: throw IllegalArgumentException("Theme name is missing.")
        require(name.length in 1..100) { "Theme name must be 1–100 characters." }
        val declaredMode = meta["dark"]?.lowercase()
        require(declaredMode == "true" || declaredMode == "false") { "Theme must explicitly declare dark: true/false." }
        val block = varsBlock.find(text)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Colorway theme has no variable block.")
        val raw = variable.findAll(comments.replace(block, "")).associate { it.groupValues[1] to it.groupValues[2].trim() }

        fun resolve(key: String, visited: Set<String> = emptySet()): Long? {
            if (key in visited || visited.size >= 8) return null
            val value = raw[key] ?: return null
            val reference = alias.matchEntire(value)
            if (reference != null) return resolve(reference.groupValues[1], visited + key)
            if (!hex.matches(value)) return null
            val expanded = if (value.length == 4) "#${value[1]}${value[1]}${value[2]}${value[2]}${value[3]}${value[3]}" else value
            return 0xFF000000L or expanded.substring(1).toLong(16)
        }
        val roles = supported.mapNotNull { key -> resolve(key)?.let { key to it } }.toMap()
        require(roles.size >= 2 && ("--primary" in roles || "--bg_base" in roles)) {
            "Theme needs at least two actual colors including a primary or background."
        }
        return CustomTheme(
            name = name,
            primaryColorArgb = roles["--primary"] ?: roles["--bg_base"]!!,
            secondaryColorArgb = roles["--primary_light"],
            tertiaryColorArgb = roles["--primary_dark"],
            importedPalette = ImportedThemePalette(dark = declaredMode == "true", roles = roles),
            source = "Colorway",
        )
    }
}
