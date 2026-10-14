package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeImportTest {
    private val ovt = """
        @OBSThemeMeta {
          name: 'Evening Study';
          dark: 'true';
          extends: 'com.myrqyry.Colorway';
        }
        @OBSThemeVars {
          /* historical color --primary: #000000; */
          --bg_base: #182024;
          --bg_window: #20282c;
          --bg_dock: #303b41;
          --bg_preview: #11171a;
          --text: #f0efed;
          --text_muted: #b7c3c5;
          --primary: #9ACBBB;
          --primary_light: #bddbd7;
          --border_color: #536064;
          --ico: var(--text);
        }
    """.trimIndent()

    @Test fun parsesLivePaletteNotAnnotatedHistoricalValues() {
        val theme = ThemeImport.parse(ovt)
        assertEquals("Evening Study", theme.name)
        assertEquals("Colorway", theme.source)
        assertEquals(0xFF9ACBBBL, theme.primaryColorArgb)
        assertEquals(0xFF182024L, theme.importedPalette!!.roles["--bg_base"])
        assertNull(theme.importedPalette!!.roles["--ico"]) // unknown mapped role
        val dark = theme.generateColorScheme(true)
        val light = theme.generateColorScheme(false)
        assertEquals(Color(0xFF182024), dark.background)
        assertEquals(Color(0xFF20282C), dark.surface)
        assertEquals(Color(0xFF9ACBBB), dark.primary)
        assertNotEquals(dark.background, light.background)
    }

    @Test fun shortHexAndAliasResolveWithCycleProtection() {
        val text = """
            @OBSThemeMeta {
                name: 'Short';
                dark: 'false';
            }
            @OBSThemeVars {
                --bg_base: #abc;
                --primary: var(--color);
                --color: #123;
                --text: var(--text);
            }
        """.trimIndent()
        val theme = ThemeImport.parse(text)
        assertEquals(0xFF112233L, theme.primaryColorArgb)
        assertEquals(0xFFAABBCCL, theme.importedPalette!!.roles["--bg_base"])
        assertFalse("--text" in theme.importedPalette!!.roles)
    }

    @Test fun importRejectsMissingModeAndInvalidCssExpressions() {
        val missingMode = ovt.replace("dark: 'true';", "")
        runCatching { ThemeImport.parse(missingMode) }.onSuccess { error("Missing explicit mode accepted") }
        val invalid = ovt.replace("#9ACBBB", "url(https://example.com)")
        val theme = ThemeImport.parse(invalid)
        assertFalse("--primary" in theme.importedPalette!!.roles)
        runCatching { ThemeImport.parse("a".repeat(98305)) }.onSuccess { error("Oversize text accepted") }
    }

    @Test fun legacyJsonAndNewJsonRoundTrip() {
        val old = """{"id":"old","name":"Saved","primaryColorArgb":4284826400}"""
        val legacy = ThemeImport.parse(old)
        assertNull(legacy.importedPalette)
        val parsed = ThemeImport.parse(ovt)
        val restored = ThemeImport.parse(Json.encodeToString(parsed))
        assertEquals(parsed.importedPalette, restored.importedPalette)
        assertEquals(parsed.primaryColorArgb, restored.primaryColorArgb)
    }

    @Test fun boundedFileInputRejectsOversizedData() {
        val text = ThemeImport.readLimited(ovt.byteInputStream())
        assertEquals(ovt, text)
        runCatching { ThemeImport.readLimited(ByteArray(98305) { 65 }.inputStream()) }
            .onSuccess { error("Oversize file input accepted") }
    }

    @Test fun contrastFallbackAvoidsIllegibleText() {
        val imported = ThemeImport.parse(
            ovt.replace("#f0efed", "#182024").replace("#9ACBBB", "#111111")
        )
        val dark = imported.generateColorScheme(true)
        assertNotEquals(Color(0xFF182024), dark.onSurface)
        assertNotEquals(dark.primary, dark.onPrimary)
    }

    @Test fun catalogFiltersPathsAndPreventsTruncatedIndex() {
        val example = """
            {"truncated":false,"tree":[
              {"path":"public/themes/Colorway-EarthBound.ovt","type":"blob"},
              {"path":"public/themes/Colorway-Magicant.ovt","type":"blob"},
              {"path":"public/themes/../secret.ovt","type":"blob"},
              {"path":"public/themes/Colorway-Ignore.ovt","type":"tree"},
              {"path":"other/Colorway-Test.ovt","type":"blob"}
            ]}
        """.trimIndent()
        val entries = ColorwayCatalog.parseTree(example)
        assertEquals(listOf("EarthBound", "Magicant"), entries.map { it.name })
        assertTrue(entries.all { it.path.startsWith("public/themes/Colorway-") })
        runCatching { ColorwayCatalog.parseTree("""{"truncated":true,"tree":[]}""") }
            .onSuccess { error("Truncated catalog accepted") }
    }
}
