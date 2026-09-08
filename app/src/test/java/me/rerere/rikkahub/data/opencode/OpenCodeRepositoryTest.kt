package me.rerere.rikkahub.data.opencode

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.rikkahub.data.db.entity.OpenCodeConnectionEntity
import me.rerere.rikkahub.data.db.entity.OpenCodeWorkspaceRefEntity

class OpenCodeRepositoryTest {
    private val connection = OpenCodeConnectionEntity("connection", "PC", "http://localhost:4096", createdAt = 1, updatedAt = 1)
    private val reference = OpenCodeWorkspaceRefEntity("ref", connection.id, "demo", "/work/demo", createdAt = 1, updatedAt = 1)

    @Test
    fun sessionLifecycleDelegatesToRemoteClient() = runBlocking {
        val store = FakeStore(connection, reference)
        val client = FakeClient()
        val repository = OpenCodeRepository(store) { client }

        assertEquals(OpenCodeSession("session", "Demo", "/work/demo"), repository.createSession(reference.id))
        assertEquals(listOf(OpenCodeMessage("m1", "assistant", "hello")), repository.getMessages(reference.id, "session"))
        assertEquals(OpenCodeMessage("m2", "assistant", "done"), repository.prompt(reference.id, "session", "hi"))
        assertTrue(repository.abortSession(reference.id, "session"))
        assertEquals("hi", client.promptText)
        assertEquals("session", client.abortedSession)
    }

    @Test
    fun connectionCreationNormalizesUrlAndStoresOnlySecretReference() = runBlocking {
        val store = FakeStore()
        val repository = OpenCodeRepository(store) { error("not used") }

        val saved = repository.createConnection(" PC ", " https://example.test/// ", "Basic abc")

        assertEquals("PC", saved.name)
        assertEquals("https://example.test", saved.baseUrl)
        assertEquals("Basic abc", store.secrets[saved.authSecretRef])
    }

    @Test
    fun remoteFailuresArePropagatedAndReferencesRemainPersisted() = runBlocking {
        val store = FakeStore(connection, reference)
        val failure = IOException("offline")
        val repository = OpenCodeRepository(store) { throw failure }

        try {
            repository.prompt(reference.id, "session", "hi")
            error("expected failure")
        } catch (actual: IOException) {
            assertSame(failure, actual)
        }
        assertEquals(reference, store.refs[reference.id])
    }

    @Test
    fun missingConnectionAndReferenceFailClearly() = runBlocking {
        val store = FakeStore()
        val repository = OpenCodeRepository(store) { error("not used") }

        assertFailsWithMessage("OpenCode connection not found") { repository.testConnection("missing") }
        assertFailsWithMessage("OpenCode workspace reference not found") { repository.getMessages("missing", "session") }
    }

    @Test
    fun unexpectedRemoteClientFailureIsPropagated() = runBlocking {
        val store = FakeStore(connection, reference)
        val repository = OpenCodeRepository(store) { error("malformed OpenCode response") }

        assertFailsWithMessage("malformed OpenCode response") { repository.createSession(reference.id) }
    }

    private fun assertFailsWithMessage(expected: String, block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            error("expected failure")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains(expected))
        }
    }

    private class FakeStore(
        connection: OpenCodeConnectionEntity? = null,
        reference: OpenCodeWorkspaceRefEntity? = null,
    ) : OpenCodeConnectionStore {
        private val connectionFlow = MutableStateFlow(connection?.let(::listOf) ?: emptyList())
        private val referenceFlow = MutableStateFlow(reference?.let(::listOf) ?: emptyList())
        val connectionsById = mutableMapOf<String, OpenCodeConnectionEntity>().apply { connection?.let { put(it.id, it) } }
        val refs = mutableMapOf<String, OpenCodeWorkspaceRefEntity>().apply { reference?.let { put(it.id, it) } }
        val secrets = mutableMapOf<String, String>()

        override val connections: Flow<List<OpenCodeConnectionEntity>> = connectionFlow
        override val workspaceRefs: Flow<List<OpenCodeWorkspaceRefEntity>> = referenceFlow
        override suspend fun getConnection(id: String) = connectionsById[id]
        override suspend fun getWorkspaceRef(id: String) = refs[id]
        override suspend fun saveConnection(connection: OpenCodeConnectionEntity) {
            connectionsById[connection.id] = connection
            connectionFlow.value = connectionsById.values.toList()
        }
        override suspend fun saveWorkspaceRef(ref: OpenCodeWorkspaceRefEntity) {
            refs[ref.id] = ref
            referenceFlow.value = refs.values.toList()
        }
        override suspend fun getSecret(ref: String) = secrets[ref]
        override suspend fun saveSecret(ref: String, value: String) {
            secrets[ref] = value
        }
        override suspend fun deleteConnection(id: String) = connectionsById.remove(id)
            .also { connectionFlow.value = connectionsById.values.toList() }
            .let { }
        override suspend fun deleteWorkspaceRef(id: String) {
            refs.remove(id)
            referenceFlow.value = refs.values.toList()
        }
    }

    private class FakeClient : OpenCodeClient {
        var promptText: String? = null
        var abortedSession: String? = null
        override suspend fun health() = OpenCodeHealth(true, "1.0")
        override suspend fun listProjects() = emptyList<OpenCodeProject>()
        override suspend fun createSession(directory: String?) = OpenCodeSession("session", "Demo", directory)
        override suspend fun listSessions(directory: String?) = emptyList<OpenCodeSession>()
        override suspend fun getSession(sessionId: String, directory: String?) = OpenCodeSession(sessionId, directory = directory)
        override suspend fun getMessages(sessionId: String, directory: String?) = listOf(OpenCodeMessage("m1", "assistant", "hello"))
        override suspend fun prompt(sessionId: String, directory: String?, text: String): OpenCodeMessage {
            promptText = text
            return OpenCodeMessage("m2", "assistant", "done")
        }
        override suspend fun abortSession(sessionId: String, directory: String?): Boolean {
            abortedSession = sessionId
            return true
        }
    }
}
