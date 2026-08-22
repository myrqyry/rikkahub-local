package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.runBlocking
import me.rerere.agentruntime.DeterministicToolResultContextPolicy
import me.rerere.agentruntime.EvidenceQuery
import me.rerere.agentruntime.EvidenceRecord
import me.rerere.agentruntime.EvidenceStore
import me.rerere.agentruntime.EvidenceWriteResult
import me.rerere.agentruntime.InMemoryEvidenceStore
import me.rerere.agentruntime.ToolResultDisposition
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolResultContextProjectorTest {
    @Test
    fun `tool text is stored before its oversized context projection`() = runBlocking {
        val evidence = InMemoryEvidenceStore()
        val projector = ToolResultContextProjector(
            evidenceStore = evidence,
            policy = DeterministicToolResultContextPolicy(maxInlineBytes = 8),
            maxInlineBytes = 8,
        )

        val projected = projector.project(
            toolName = "read_file",
            toolCallId = "call-1",
            conversationId = "chat-1",
            output = listOf(UIMessagePart.Text("x".repeat(100))),
        )

        assertEquals("x".repeat(100), evidence.get("tool:chat-1:call-1")!!.payload)
        assertTrue((projected.single() as UIMessagePart.Text).text.length < 100)
    }

    @Test
    fun `tool projection keeps non text parts unchanged`() = runBlocking {
        val image = UIMessagePart.Image("content://image")
        val projected = ToolResultContextProjector(
            evidenceStore = InMemoryEvidenceStore(),
            policy = DeterministicToolResultContextPolicy(maxInlineBytes = 8),
            maxInlineBytes = 8,
        ).project(
            toolName = "camera",
            toolCallId = "call-2",
            conversationId = "chat-1",
            output = listOf(UIMessagePart.Text("ok"), image),
        )

        assertEquals(image, projected.last())
    }

    @Test
    fun `failed evidence storage never emits a reference`() = runBlocking {
        val projected = ToolResultContextProjector(
            evidenceStore = object : EvidenceStore {
                override suspend fun put(record: EvidenceRecord): EvidenceWriteResult = error("storage unavailable")
                override suspend fun get(id: String): EvidenceRecord? = null
                override suspend fun query(query: EvidenceQuery): List<EvidenceRecord> = emptyList()
            },
            policy = { ToolResultDisposition.REFERENCE_ONLY },
            maxInlineBytes = 8,
        ).project(
            toolName = "read_file",
            toolCallId = "call-3",
            conversationId = "chat-1",
            output = listOf(UIMessagePart.Text("hello world")),
        )

        assertEquals("hello...", (projected.single() as UIMessagePart.Text).text)
    }
}
