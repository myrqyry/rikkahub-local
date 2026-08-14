package me.rerere.locallm.litert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityScopesTest {

    private fun scopes(
        file: List<FileScope> = emptyList(),
        network: List<NetworkScope> = emptyList(),
        model: List<ModelScope> = emptyList(),
    ) = CapabilityScopes(fileScopes = file, networkScopes = network, modelScopes = model)

    @Test
    fun `empty scopes allow everything`() {
        val g = CapabilityScopes()
        assertTrue(g.isAllowingFile("/a/b.txt", "write"))
        assertTrue(g.isAllowingNetwork("https://huggingface.co", "GET"))
        assertTrue(g.isAllowingModel())
    }

    @Test
    fun `file scope matches exact path and operation`() {
        val g = scopes(file = listOf(FileScope("/store", "write")))
        assertTrue(g.isAllowingFile("/store", "write"))
        assertFalse(g.isAllowingFile("/store", "read"))
        assertFalse(g.isAllowingFile("/other", "write"))
    }

    @Test
    fun `file wildcard path allows any path for the operation`() {
        val g = scopes(file = listOf(FileScope("*", "write")))
        assertTrue(g.isAllowingFile("/anything", "write"))
        assertFalse(g.isAllowingFile("/anything", "delete"))
    }

    @Test
    fun `network scope matches origin and method`() {
        val g = scopes(network = listOf(NetworkScope("https://huggingface.co", "GET")))
        assertTrue(g.isAllowingNetwork("https://huggingface.co", "GET"))
        assertFalse(g.isAllowingNetwork("https://huggingface.co", "POST"))
        assertFalse(g.isAllowingNetwork("https://evil.example", "GET"))
    }

    @Test
    fun `network scope without method allows any method`() {
        val g = scopes(network = listOf(NetworkScope("https://huggingface.co")))
        assertTrue(g.isAllowingNetwork("https://huggingface.co", "POST"))
    }

    @Test
    fun `model scope matches source and maxBytes`() {
        val g = scopes(model = listOf(ModelScope("huggingface.co", 8L * 1024 * 1024 * 1024)))
        assertTrue(g.isAllowingModel("huggingface.co", 8L * 1024 * 1024 * 1024))
        assertFalse(g.isAllowingModel("elsewhere", 8L * 1024 * 1024 * 1024))
        assertFalse(g.isAllowingModel("huggingface.co", 16L * 1024 * 1024 * 1024))
    }

    @Test
    fun `capability grant remains backward compatible with flat lists`() {
        val g = CapabilityGrant(listOf("read"), listOf("read"), listOf("write"))
        assertTrue(g.isAllowed("read"))
        assertFalse(g.isAllowed("write"))
        assertEquals(CapabilityScopes(), g.scopes)
        assertNull(g.scopes.fileScopes.firstOrNull())
    }

    @Test
    fun `capability grant carries typed scopes`() {
        val g = CapabilityGrant(
            listOf("model.install"),
            listOf("model.install"),
            emptyList(),
            CapabilityScopes(
                networkScopes = listOf(NetworkScope("https://huggingface.co")),
                fileScopes = listOf(FileScope("privateModelStore", "write")),
                modelScopes = listOf(ModelScope("huggingface.co", 8L * 1024 * 1024 * 1024)),
            ),
        )
        assertTrue(g.isAllowed("model.install"))
        assertTrue(g.scopes.isAllowingNetwork("https://huggingface.co"))
        assertTrue(g.scopes.isAllowingFile("privateModelStore", "write"))
        assertTrue(g.scopes.isAllowingModel("huggingface.co", 8L * 1024 * 1024 * 1024))
    }

    @Test
    fun `resolver maps a tool name to its scopes`() {
        val resolver = CapabilityScopeResolver { name ->
            if (name == "model.install") {
                CapabilityScopes(
                    networkScopes = listOf(NetworkScope("https://huggingface.co")),
                    modelScopes = listOf(ModelScope("huggingface.co", 8L * 1024 * 1024 * 1024)),
                )
            } else {
                CapabilityScopes()
            }
        }
        val install = resolver.scopesFor("model.install")
        assertTrue(install.isAllowingNetwork("https://huggingface.co"))
        assertTrue(install.isAllowingModel("huggingface.co"))
        assertEquals(CapabilityScopes(), resolver.scopesFor("some.other.tool"))
    }
}
