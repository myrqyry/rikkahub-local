package me.rerere.locallm.litert.artifact

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtifactRefTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `fully populated artifact ref round-trips`() {
        val ref = ArtifactRef(
            id = "a-1",
            kind = ArtifactKind.PROCESS_OUTPUT,
            name = "build.log",
            path = "/data/artifacts/build.log",
            uri = "file:///data/artifacts/build.log",
            mimeType = "text/plain",
            byteSize = 2048L,
            createdAtMs = 1700000000000L,
            metadata = mapOf("tool" to "workspace_shell"),
        )

        val encoded = json.encodeToString(ArtifactRef.serializer(), ref)
        val decoded = json.decodeFromString(ArtifactRef.serializer(), encoded)

        assertEquals(ref, decoded)
        assertEquals("build.log", decoded.name)
        assertEquals(ArtifactKind.PROCESS_OUTPUT, decoded.kind)
    }

    @Test
    fun `minimal artifact ref round-trips`() {
        val ref = ArtifactRef(id = "a-2", kind = ArtifactKind.FILE, name = "notes.txt")

        val encoded = json.encodeToString(ArtifactRef.serializer(), ref)
        val decoded = json.decodeFromString(ArtifactRef.serializer(), encoded)

        assertEquals(ref, decoded)
        assertNull(decoded.path)
        assertNull(decoded.uri)
        assertNull(decoded.mimeType)
        assertNull(decoded.byteSize)
        assertNull(decoded.createdAtMs)
        assertEquals(emptyMap<String, String>(), decoded.metadata)
    }

    @Test
    fun `nullable fields deserialize to null when absent`() {
        val raw = """{"id":"a-3","kind":"IMAGE","name":"shot.png"}"""

        val decoded = json.decodeFromString(ArtifactRef.serializer(), raw)

        assertEquals("a-3", decoded.id)
        assertEquals(ArtifactKind.IMAGE, decoded.kind)
        assertEquals("shot.png", decoded.name)
        assertNull(decoded.path)
        assertNull(decoded.uri)
        assertNull(decoded.byteSize)
    }

    @Test
    fun `every artifact kind round-trips`() {
        for (kind in ArtifactKind.entries) {
            val ref = ArtifactRef(id = "k-${kind.name}", kind = kind, name = kind.name)

            val decoded = json.decodeFromString(
                ArtifactRef.serializer(),
                json.encodeToString(ArtifactRef.serializer(), ref),
            )

            assertEquals(ref, decoded)
        }
    }
}
