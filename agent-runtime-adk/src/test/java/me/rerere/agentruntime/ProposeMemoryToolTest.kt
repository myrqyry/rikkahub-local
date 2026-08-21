package me.rerere.agentruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposeMemoryToolTest {

    private fun store() = MemoryStore()

    @Test
    fun `tool exposes proposeMemory name and declaration`() {
        val tool = ProposeMemoryTool(store = store(), agentName = "assistant")
        assertEquals("proposeMemory", tool.name)
        assertEquals("proposeMemory", tool.declaration().name)
        assertTrue(tool.description.contains("NOT written"))
    }

    @Test
    fun `execute with valid args proposes a pending memory and commits nothing`() {
        val memoryStore = store()
        val result = proposeMemoryToolRun(store = memoryStore, agentName = "assistant", args = mapOf("title" to "Stack", "content" to "Kotlin + Compose", "level" to "project"))
        val text = (result as Map<*, *>)["result"].toString()
        assertTrue(text.contains("proposed"))
        assertTrue(text.contains("Stack"))
        assertTrue(text.contains("pending user acceptance"))
        assertEquals(1, memoryStore.pendingProposals().size)
        assertEquals(null, memoryStore.listMemories(MemoryLevel.PROJECT))
        val accepted = memoryStore.accept(memoryStore.pendingProposals().first().id)
        assertEquals("Kotlin + Compose", accepted?.content)
        assertEquals(1, memoryStore.listMemories(MemoryLevel.PROJECT)?.size)
    }

    @Test
    fun `execute with missing title or content returns an error`() {
        val memoryStore = store()
        val noTitle = proposeMemoryToolRun(store = memoryStore, agentName = "assistant", args = mapOf("content" to "x"))
        assertTrue((noTitle as Map<*, *>)["error"].toString().contains("title"))
        val noContent = proposeMemoryToolRun(store = memoryStore, agentName = "assistant", args = mapOf("title" to "T"))
        assertTrue((noContent as Map<*, *>)["error"].toString().contains("content"))
        assertEquals(0, memoryStore.pendingProposals().size)
    }

    @Test
    fun `default level is user`() {
        val memoryStore = store()
        val result = proposeMemoryToolRun(store = memoryStore, agentName = "assistant", args = mapOf("title" to "Pref", "content" to "short replies"))
        assertTrue((result as Map<*, *>)["result"].toString().contains("USER level"))
        assertEquals(1, memoryStore.pendingProposals().size)
        assertEquals(MemoryLevel.USER, memoryStore.pendingProposals().first().memory.level)
    }
}
