package me.rerere.rikkahub.data.modelregistry

import org.junit.Assert.*
import org.junit.Test

class ModelsDevProviderDirectoryTest {
    private val fixture = """
        {
          "zed-host": {
            "id": "zed-host", "name": "Zed Host",
            "npm": "@ai-sdk/openai-compatible",
            "api": "https://api.zed.example/v1",
            "models": {
              "a-model": {"id": "a-model", "name": "A"},
              "b-model": {"id": "b-model", "name": "B"}
            }
          },
          "native-host": {
            "id": "native-host", "name": "Native Host",
            "npm": "@ai-sdk/anthropic",
            "api": "https://native.example/v1",
            "models": {}
          },
          "openai": {
            "id": "openai", "name": "OpenAI",
            "npm": "@ai-sdk/openai",
            "models": {}
          }
        }
    """.trimIndent()

    @Test fun parsesAllProviderListingsButOnlyMarksCompatibleApiEndpointsAsAddable() {
        val providers = ModelsDevProviderDirectory.parse(fixture)
        assertEquals(listOf("Native Host", "OpenAI", "Zed Host"), providers.map { it.name })
        val zed = providers.first { it.id == "zed-host" }
        assertEquals("https://api.zed.example/v1", zed.baseUrl)
        assertEquals(2, zed.modelCount)
        assertTrue(zed.canAddAsOpenAiCompatible)
        assertFalse(providers.first { it.id == "native-host" }.canAddAsOpenAiCompatible)
        assertFalse(providers.first { it.id == "openai" }.canAddAsOpenAiCompatible)
    }

    @Test fun blocksUntrustedEndpointSchemesEvenWhenProviderClaimsCompatibility() {
        val providers = ModelsDevProviderDirectory.parse("""
            {
              "unsafe": {"name":"Unsafe","npm":"@ai-sdk/openai-compatible",
                  "api":"http://example.com/v1","models":{}},
              "js": {"name":"Script","npm":"@ai-sdk/openai-compatible",
                  "api":"javascript:alert(1)","models":{}},
              "localhost": {"name":"Localhost","npm":"@ai-sdk/openai-compatible",
                  "api":"http://127.0.0.1:11434/v1","models":{}}
            }
        """.trimIndent())
        assertFalse(providers.first { it.id == "unsafe" }.canAddAsOpenAiCompatible)
        assertFalse(providers.first { it.id == "js" }.canAddAsOpenAiCompatible)
        assertTrue(providers.first { it.id == "localhost" }.canAddAsOpenAiCompatible)
    }

    @Test fun catalogSuggestionCreatesDisabledEmptyKeyProviderAndNoAutoImportedModels() {
        val entry = ModelsDevProviderDirectory.parse(fixture).first { it.id == "zed-host" }
        val draft = entry.toProviderDraft()
        assertEquals("Zed Host", draft.name)
        assertEquals("https://api.zed.example/v1", draft.baseUrl)
        assertEquals("", draft.apiKey)
        assertFalse(draft.enabled)
        assertFalse(draft.builtIn)
        assertTrue(draft.models.isEmpty())
    }

    @Test fun parserRejectsMalformedCatalogInsteadOfPresentingEmptyValidCatalog() {
        try {
            ModelsDevProviderDirectory.parse("""["not a provider object"]""")
            fail("Expected invalid catalog failure")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun providerIdMustMatchCatalogKeyAndCannotUseAnEmptyName() {
        val parsed = ModelsDevProviderDirectory.parse("""
        {
          "one": {"id":"someone-else","name":"Do Not Trust","npm":"@ai-sdk/openai-compatible","api":"https://safe.example/v1"},
          "two": {"id":"two","name":"","npm":"@ai-sdk/openai-compatible","api":"https://safe.example/v1"}
        }
        """.trimIndent())
        assertTrue(parsed.isEmpty())
    }
}
