package me.rerere.rikkahub.data.catalog

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.skills.imports.ArtifactKind
import me.rerere.rikkahub.skills.imports.ArtifactSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BundledCatalogAdapterTest {
    private val catalogJson = """
        {
          "version": 1,
          "updated_at": "2026-08-05",
          "skills": [
            {
              "name": "weather",
              "title": "Weather Lookup",
              "description": "Get weather",
              "source_url": "https://github.com/acme/skill-weather",
              "is_bundled": true
            },
            {
              "name": "quote",
              "title": "Quote",
              "description": "Random quote"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `bundled catalog parses to normalized entries`() = runBlocking {
        val adapter = BundledCatalogAdapter(rawCatalogJson = catalogJson)
        val catalog = adapter.fetchCatalog()
        assertEquals("bundled-skills", adapter.id)
        assertEquals(1, catalog.entries.size)
        val weather = catalog.entries.first { it.name == "weather" }
        assertEquals(ArtifactKind.SKILL, weather.kind)
        assertEquals(ArtifactSourceKind.URL, weather.sourceKind)
        assertEquals("https://github.com/acme/skill-weather", weather.source)
        assertNull(weather.expectedSha256)
    }

    @Test
    fun `empty or malformed catalog yields empty entries`() = runBlocking {
        assertEquals(0, BundledCatalogAdapter(rawCatalogJson = "{ nope").fetchCatalog().entries.size)
        assertEquals(0, BundledCatalogAdapter(rawCatalogJson = null).fetchCatalog().entries.size)
    }
}
