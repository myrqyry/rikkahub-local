package me.rerere.locallm.litert

import kotlinx.serialization.Serializable

/**
 * Typed effect / resource scopes that sit beneath a flat tool-name grant.
 *
 * A tool name is a convenient top-level permission; it resolves into the effects and
 * resources it may touch (see [CapabilityScopeResolver]). These scopes are the granular
 * check the capability broker applies. All fields default to empty so an existing
 * flat `CapabilityGrant(requested, granted, rejected)` continues to compile unchanged.
 */
@Serializable
data class CapabilityScopes(
    val toolScopes: List<String> = emptyList(),
    val fileScopes: List<FileScope> = emptyList(),
    val networkScopes: List<NetworkScope> = emptyList(),
    val credentialScopes: List<String> = emptyList(),
    val sharingScopes: List<String> = emptyList(),
    val modelScopes: List<ModelScope> = emptyList(),
    val executionScopes: List<String> = emptyList(),
) {
    fun isAllowingFile(path: String, operation: String): Boolean =
        fileScopes.isEmpty() || fileScopes.any { it.operation == operation && (it.path == "*" || it.path == path) }

    fun isAllowingNetwork(origin: String, method: String? = null): Boolean {
        if (networkScopes.isEmpty()) return true
        return networkScopes.any {
            (it.origin == "*" || it.origin == origin) && (method == null || it.method == null || it.method == method)
        }
    }

    fun isAllowingModel(source: String? = null, maxBytes: Long? = null): Boolean =
        modelScopes.isEmpty() || modelScopes.any {
            (source == null || it.source == null || it.source == source) &&
                (maxBytes == null || it.maxBytes == null || it.maxBytes == maxBytes)
        }
}

/** A filesystem path scope; `path` may be "*" for any path under the granted operation. */
@Serializable
data class FileScope(
    val path: String,
    val operation: String, // read | write | delete | ...
)

/** A network origin scope; `origin` may be "*". */
@Serializable
data class NetworkScope(
    val origin: String,
    val method: String? = null,
)

/** A model resource scope. */
@Serializable
data class ModelScope(
    val source: String? = null,
    val maxBytes: Long? = null,
)

/**
 * Resolves a tool name into the typed scopes it requires. Deterministic; never executes.
 * The app-side broker implements this against its tool registry (the seam lives here so
 * local-llm stays pure and JVM-testable).
 */
fun interface CapabilityScopeResolver {
    fun scopesFor(toolName: String): CapabilityScopes
}
