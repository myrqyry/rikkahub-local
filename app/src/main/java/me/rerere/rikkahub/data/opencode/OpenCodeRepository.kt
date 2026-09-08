package me.rerere.rikkahub.data.opencode

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import me.rerere.rikkahub.data.db.entity.OpenCodeConnectionEntity
import me.rerere.rikkahub.data.db.entity.OpenCodeWorkspaceRefEntity
import java.util.UUID
import java.net.URI

data class OpenCodeWorkspaceRow(
    val connection: OpenCodeConnectionEntity,
    val reference: OpenCodeWorkspaceRefEntity?,
)

class OpenCodeRepository(
    private val store: OpenCodeConnectionStore,
    private val clientFactory: suspend (OpenCodeConnectionEntity) -> OpenCodeClient,
) {
    fun observeWorkspaceRows(): Flow<List<OpenCodeWorkspaceRow>> =
        store.connections.combine(store.workspaceRefs) { connections, refs ->
            connections.flatMap { connection ->
                val connectionRefs = refs.filter { it.connectionId == connection.id }
                if (connectionRefs.isEmpty()) listOf(OpenCodeWorkspaceRow(connection, null))
                else connectionRefs.map { OpenCodeWorkspaceRow(connection, it) }
            }
        }

    suspend fun createConnection(name: String, baseUrl: String, credential: String?): OpenCodeConnectionEntity {
        val normalizedUrl = normalizeUrl(baseUrl)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val secretRef = credential?.takeIf { it.isNotBlank() }?.let {
            "opencode:$id".also { ref -> store.saveSecret(ref, credential) }
        }
        return OpenCodeConnectionEntity(
            id = id,
            name = name.trim(),
            baseUrl = normalizedUrl,
            authSecretRef = secretRef,
            createdAt = now,
            updatedAt = now,
        ).also { store.saveConnection(it) }
    }

    suspend fun testConnection(connectionId: String): OpenCodeHealth {
        val connection = requireConnection(connectionId)
        return runCatching { client(connection).health() }.onSuccess { health ->
            store.saveConnection(connection.copy(
                lastHealthStatus = if (health.healthy) "HEALTHY" else "UNHEALTHY",
                serverVersion = health.version,
                updatedAt = System.currentTimeMillis(),
            ))
        }.getOrElse { error ->
            store.saveConnection(connection.copy(
                lastHealthStatus = "ERROR",
                updatedAt = System.currentTimeMillis(),
            ))
            throw error
        }
    }

    suspend fun discoverProjects(connectionId: String): List<OpenCodeProject> {
        val connection = requireConnection(connectionId)
        return client(connection).listProjects()
    }

    suspend fun selectProject(connectionId: String, project: OpenCodeProject): OpenCodeWorkspaceRefEntity {
        val connection = requireConnection(connectionId)
        val now = System.currentTimeMillis()
        return OpenCodeWorkspaceRefEntity(
            id = UUID.randomUUID().toString(),
            connectionId = connection.id,
            name = project.name?.takeIf { it.isNotBlank() } ?: project.directory.substringAfterLast('/'),
            remoteDirectory = project.directory,
            remoteProjectId = project.id,
            createdAt = now,
            updatedAt = now,
        ).also { store.saveWorkspaceRef(it) }
    }

    suspend fun createSession(refId: String): OpenCodeSession {
        val ref = requireRef(refId)
        return client(requireConnection(ref.connectionId)).createSession(ref.remoteDirectory)
    }

    suspend fun prompt(refId: String, sessionId: String, text: String): OpenCodeMessage {
        val ref = requireRef(refId)
        return client(requireConnection(ref.connectionId)).prompt(sessionId, ref.remoteDirectory, text)
    }

    suspend fun getMessages(refId: String, sessionId: String): List<OpenCodeMessage> {
        val ref = requireRef(refId)
        return client(requireConnection(ref.connectionId)).getMessages(sessionId, ref.remoteDirectory)
    }

    suspend fun abortSession(refId: String, sessionId: String): Boolean {
        val ref = requireRef(refId)
        return client(requireConnection(ref.connectionId)).abortSession(sessionId, ref.remoteDirectory)
    }

    private suspend fun requireConnection(id: String) = store.getConnection(id)
        ?: error("OpenCode connection not found: $id")

    private suspend fun requireRef(id: String) = store.getWorkspaceRef(id)
        ?: error("OpenCode workspace reference not found: $id")

    private suspend fun client(connection: OpenCodeConnectionEntity): OpenCodeClient = clientFactory(connection)

    private fun normalizeUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        require(trimmed.isNotBlank()) { "OpenCode URL is required" }
        val parsed = URI.create(trimmed)
        require(parsed.scheme == "http" || parsed.scheme == "https") { "OpenCode URL must use HTTP or HTTPS" }
        require(!parsed.host.isNullOrBlank()) { "OpenCode URL must include a host" }
        return trimmed
    }
}
