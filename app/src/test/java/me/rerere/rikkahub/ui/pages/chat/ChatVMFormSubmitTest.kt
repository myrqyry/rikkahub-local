package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.ui.components.message.resolveNavigationDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatVMFormSubmitTest {

    @Test
    fun `allowlisted destinations resolve`() {
        assertEquals("/images", resolveNavigationDestination("/images"))
        assertEquals("/gallery", resolveNavigationDestination("/gallery"))
        assertEquals("/files", resolveNavigationDestination("/files"))
        assertEquals("/workspace", resolveNavigationDestination("/workspace"))
    }

    @Test
    fun `non-allowlisted destinations return null`() {
        assertNull(resolveNavigationDestination("/nonexistent"))
        assertNull(resolveNavigationDestination("/admin"))
    }

    @Test
    fun `custom allowlist is respected`() {
        val allowlist = setOf("/only")
        assertEquals("/only", resolveNavigationDestination("/only", allowlist))
        assertNull(resolveNavigationDestination("/images", allowlist))
    }
}
