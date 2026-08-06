package me.rerere.rikkahub.data.share

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundShareTest {
    private fun normalizeText(rawText: String?): NormalizedShare? =
        InboundShareNormalizer.classify(Intent.ACTION_SEND, rawText, null)

    @Test
    fun `plain text normalizes to Text`() {
        val p = normalizeText("hello world")
        assertTrue(p is NormalizedShare.Text)
        assertEquals("hello world", (p as NormalizedShare.Text).text)
    }

    @Test
    fun `http url normalizes to Url`() {
        val p = normalizeText("https://example.com/page")
        assertTrue(p is NormalizedShare.Url)
        assertEquals("https://example.com/page", (p as NormalizedShare.Url).url)
    }

    @Test
    fun `content uri stream normalizes to File`() {
        val p = InboundShareNormalizer.classify(Intent.ACTION_SEND, null, "content://com.example/shared/1.png")
        assertTrue(p is NormalizedShare.File)
        assertEquals("content://com.example/shared/1.png", (p as NormalizedShare.File).uri)
    }

    @Test
    fun `non content uri stream is rejected`() {
        assertNull(InboundShareNormalizer.classify(Intent.ACTION_SEND, null, "file:///sdcard/x.png"))
    }

    @Test
    fun `store round trips handoff by id`() = kotlinx.coroutines.runBlocking {
        val store = InMemorySharedPayloadStore()
        val id = store.put(SharedPayloadHandoff("h1", InboundSharePayload.Text("hi"), 123L))
        assertEquals("h1", id)
        assertEquals("hi", (store.get("h1")!!.payload as InboundSharePayload.Text).text)
        store.remove("h1")
        assertNull(store.get("h1"))
    }
}
