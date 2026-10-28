package me.rerere.rikkahub.data.modelregistry

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ModelsDevProviderCacheTest {
    private val payload = """
        {"example":{"id":"example","name":"Example","npm":"@ai-sdk/openai-compatible",
        "api":"https://example.test/v1","models":{"model-a":{"id":"model-a"}}}}
    """.trimIndent()

    @Test fun freshCatalogIsReusedWithoutMakingAnotherNetworkRequest() {
        val cache = tempCache()
        try {
            val now = System.currentTimeMillis()
            val first = ModelsDevProviderDirectory.load(cache, fetch = { payload }, nowMillis = now)
            val second = ModelsDevProviderDirectory.load(
                cache, fetch = { error("Network should not be called") }, nowMillis = now
            )
            assertFalse(first.fromCache)
            assertEquals(1, first.providers.size)
            assertTrue(second.fromCache)
            assertFalse(second.stale)
            assertEquals(first.providers, second.providers)
        } finally { cache.delete() }
    }

    @Test fun staleCatalogRemainsBrowsableWhenNetworkIsUnavailable() {
        val cache = tempCache()
        try {
            val now = System.currentTimeMillis()
            ModelsDevProviderDirectory.load(cache, fetch = { payload }, nowMillis = now)
            val result = ModelsDevProviderDirectory.load(
                cache,
                fetch = { throw IOException("Offline") },
                nowMillis = now + 2 * 24 * 60 * 60 * 1000L,
            )
            assertTrue(result.fromCache)
            assertTrue(result.stale)
            assertEquals("Example", result.providers.single().name)
        } finally { cache.delete() }
    }

    @Test fun noCacheAndFailedRequestReportsFailureInsteadOfInventingProviders() {
        val cache = tempCache()
        try {
            try {
                ModelsDevProviderDirectory.load(cache, fetch = { throw IOException("Offline") })
                fail("Expected missing catalog failure")
            } catch (_: IOException) {}
        } finally { cache.delete() }
    }

    private fun tempCache(): File = File.createTempFile("models-dev-", ".json").also { it.delete() }
}
