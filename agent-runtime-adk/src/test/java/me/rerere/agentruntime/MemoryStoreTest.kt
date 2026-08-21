package me.rerere.agentruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStoreTest {

    private fun store() = MemoryStore()

    @Test
    fun `propose adds a pending proposal`() {
        val store = store()
        val proposal = store.propose(Memory("Rust borrow checker", "Borrow checker prevents use-after-free at compile time.", MemoryLevel.PROJECT))
        assertEquals(1, store.pendingProposals().size)
        assertTrue(store.pendingProposals().contains(proposal))
        assertNull(store.listMemories(MemoryLevel.PROJECT))
    }

    @Test
    fun `accept commits the proposal to committed memory`() {
        val store = store()
        val proposal = store.propose(Memory("Rust borrow checker", "Borrow checker prevents use-after-free at compile time.", MemoryLevel.PROJECT))
        val accepted = store.accept(proposal.id)
        assertNotNull(accepted)
        assertEquals("Rust borrow checker", accepted?.title)
        assertEquals(0, store.pendingProposals().size)
        assertEquals(1, store.listMemories(MemoryLevel.PROJECT)?.size)
    }

    @Test
    fun `reject discards the proposal without committing`() {
        val store = store()
        val proposal = store.propose(Memory("Draft idea", "Not sure this is true yet.", MemoryLevel.USER))
        assertTrue(store.reject(proposal.id))
        assertEquals(0, store.pendingProposals().size)
        assertNull(store.listMemories(MemoryLevel.USER))
    }

    @Test
    fun `accept with unknown id returns null`() {
        val store = store()
        val proposal = store.propose(Memory("A", "B", MemoryLevel.PROJECT))
        store.reject(proposal.id)
        assertNull(store.accept(proposal.id))
    }

    @Test
    fun `accept upserts when same title and level already committed`() {
        val store = store()
        val p1 = store.propose(Memory("Build", "Use ./gradlew assembleDebug", MemoryLevel.PROJECT))
        store.accept(p1.id)
        val p2 = store.propose(Memory("Build", "Use ./gradlew test then ./gradlew assembleDebug", MemoryLevel.PROJECT))
        store.accept(p2.id)
        assertEquals(1, store.listMemories(MemoryLevel.PROJECT)?.size)
        assertEquals("Use ./gradlew test then ./gradlew assembleDebug", store.listMemories(MemoryLevel.PROJECT)?.first()?.content)
    }

    @Test
    fun `propose replaces a pending proposal with the same title and level`() {
        val store = store()
        val first = store.propose(Memory("T", "v1", MemoryLevel.USER))
        val second = store.propose(Memory("T", "v2", MemoryLevel.USER))
        assertEquals(1, store.pendingProposals().size)
        assertNull(store.accept(first.id))
        assertEquals("v2", store.accept(second.id)?.content)
    }

    @Test
    fun `listMemories distinguishes levels`() {
        val store = store()
        val user = store.propose(Memory("Preference", "Prefers dark mode", MemoryLevel.USER))
        val proj = store.propose(Memory("Stack", "Kotlin + Compose", MemoryLevel.PROJECT))
        store.accept(user.id)
        store.accept(proj.id)
        assertEquals(1, store.listMemories(MemoryLevel.USER)?.size)
        assertEquals(1, store.listMemories(MemoryLevel.PROJECT)?.size)
        assertEquals(2, store.listMemories()?.size)
    }

    @Test
    fun `readMemory returns full content by title`() {
        val store = store()
        val proposal = store.propose(Memory("Stack", "Kotlin + Compose", MemoryLevel.PROJECT))
        store.accept(proposal.id)
        val memory = store.readMemory("Stack")
        assertNotNull(memory)
        assertEquals("Kotlin + Compose", memory?.content)
        assertNull(store.readMemory("Missing"))
    }

    @Test
    fun `proposeMemory records a pending proposal and returns a readable result`() {
        val store = store()
        val text = MemoryTools.proposeMemory(store, agentName = "demo", title = "Tip", content = "Remember to hydrate.", level = "user")
        assertTrue(text.contains("proposed"))
        assertTrue(text.contains("Tip"))
        val proposal = store.pendingProposals().singleOrNull()
        assertNotNull(proposal)
        assertEquals("Tip", proposal?.memory?.title)
        assertEquals(MemoryLevel.USER, proposal?.memory?.level)
    }
}
