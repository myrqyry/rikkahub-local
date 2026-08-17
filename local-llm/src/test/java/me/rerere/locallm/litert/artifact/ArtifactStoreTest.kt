package me.rerere.locallm.litert.artifact

import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtifactStoreTest {

    private class InMemoryStore(
        private val backing: MutableMap<String, ByteArray> = mutableMapOf(),
    ) : ArtifactSink, ArtifactResolver {

        override suspend fun write(kind: ArtifactKind, name: String, mimeType: String?, bytes: ByteArray): ArtifactRef {
            val ref = ArtifactRef(
                id = "mem-${backing.size}",
                kind = kind,
                name = name,
                mimeType = mimeType,
                byteSize = bytes.size.toLong(),
            )
            backing[ref.id] = bytes
            return ref
        }

        override suspend fun resolve(ref: ArtifactRef): ByteArray? = backing[ref.id]

        override suspend fun open(ref: ArtifactRef): InputStream? = backing[ref.id]?.inputStream()
    }

    @Test
    fun `sink write produces a resolvable ref`() = runBlocking {
        val store = InMemoryStore()
        val bytes = byteArrayOf(1, 2, 3, 4)

        val ref = store.write(ArtifactKind.TEXT, "doc.txt", "text/plain", bytes)

        assertEquals("text/plain", ref.mimeType)
        assertEquals(4L, ref.byteSize)
        assertArrayEquals(bytes, store.resolve(ref))
    }

    @Test
    fun `open returns a stream over the bytes`() = runBlocking {
        val store = InMemoryStore()
        val bytes = byteArrayOf(9, 8, 7)
        val ref = store.write(ArtifactKind.FILE, "data.bin", "application/octet-stream", bytes)

        val stream = store.open(ref)

        assertArrayEquals(bytes, stream!!.readBytes())
    }

    @Test
    fun `resolve returns null for an unknown ref`() = runBlocking {
        val store = InMemoryStore()
        val unknown = ArtifactRef(id = "missing", kind = ArtifactKind.FILE, name = "nope.txt")

        assertNull(store.resolve(unknown))
    }
}
