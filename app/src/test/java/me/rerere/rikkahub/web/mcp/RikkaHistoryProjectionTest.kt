package me.rerere.rikkahub.web.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import kotlin.uuid.Uuid

class RikkaHistoryProjectionTest {
    private val assistant = Uuid.random()
    private val otherAssistant = Uuid.random()
    private val nodeId = Uuid.random()

    private fun message(role: MessageRole, vararg parts: UIMessagePart) =
        UIMessage(role = role, parts = parts.toList())

    private fun conversation(
        owner: Uuid = assistant,
        id: Uuid = Uuid.random(),
        nodes: List<MessageNode>,
    ) = Conversation(id = id, assistantId = owner, title = "Private title", messageNodes = nodes)

    private fun hit(conversation: Conversation, node: MessageNode, message: UIMessage) =
        MessageSearchResult(node.id.toString(), message.id.toString(), conversation.id.toString(),
            conversation.title, Instant.parse("2026-10-20T00:00:00Z"), "FTS snippet")

    @Test
    fun `history MCP tools are only exported over authenticated JWT route`() {
        assertTrue(historyMcpAccessAllowed(true, true))
        assertFalse(historyMcpAccessAllowed(false, true))
        assertFalse(historyMcpAccessAllowed(true, false))
    }

    @Test
    fun `search excludes foreign assistants and unselected branches before limiting results`() = runBlocking {
        val old = message(MessageRole.USER, UIMessagePart.Text("unselected match"))
        val selected = message(MessageRole.ASSISTANT, UIMessagePart.Text("selected match"))
        val node = MessageNode(id = nodeId, messages = listOf(old, selected), selectIndex = 1)
        val allowed = conversation(nodes = listOf(node))
        val foreign = conversation(owner = otherAssistant, nodes = listOf(node))
        val candidates = listOf(hit(foreign, node, selected), hit(allowed, node, old), hit(allowed, node, selected))
        val loaded = mapOf(allowed.id to allowed, foreign.id to foreign)
        val results = selectVisibleHistoryHits(candidates, assistant, 1) { loaded[it] }
        assertEquals(listOf(selected.id.toString()), results.map { it.messageId })
        assertEquals(listOf(allowed.id.toString()), results.map { it.conversationId })
    }

    @Test
    fun `search does not expose system, tool or reasoning-only message hits`() = runBlocking {
        val system = message(MessageRole.SYSTEM, UIMessagePart.Text("system data"))
        val tool = message(MessageRole.TOOL, UIMessagePart.Text("tool data"))
        val reasoning = message(MessageRole.ASSISTANT, UIMessagePart.Reasoning("private reasoning"))
        val user = message(MessageRole.USER, UIMessagePart.Text("visible"))
        val nodes = listOf(system, tool, reasoning, user).map { MessageNode(messages = listOf(it)) }
        val convo = conversation(nodes = nodes)
        val found = selectVisibleHistoryHits(
            nodes.map { hit(convo, it, it.currentMessage) }, assistant, 15
        ) { convo }
        assertEquals(listOf(user.id.toString()), found.map { it.messageId })
    }

    @Test
    fun `context includes only selected branch user and assistant text, preserving order and anchor`() {
        val first = message(MessageRole.USER, UIMessagePart.Text("hello"))
        val stale = message(MessageRole.ASSISTANT, UIMessagePart.Text("old answer"))
        val live = message(MessageRole.ASSISTANT, UIMessagePart.Text("new answer"),
            UIMessagePart.Reasoning("private thought"), UIMessagePart.Image("file:///private.png"),
            UIMessagePart.Text("second part"))
        val tool = message(MessageRole.TOOL, UIMessagePart.Text("secret tool payload"))
        val nodes = listOf(
            MessageNode(messages = listOf(first)),
            MessageNode(id = nodeId, messages = listOf(stale, live), selectIndex = 1),
            MessageNode(messages = listOf(tool))
        )
        val result = projectHistoryContext(conversation(nodes = nodes), assistant, nodeId, 1, 1)
        val messages = result["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("hello", messages[0].jsonObjectValue("text"))
        assertEquals("new answer\nsecond part", messages[1].jsonObjectValue("text"))
        assertEquals("true", messages[1].jsonObjectValue("is_anchor"))
        assertFalse(result.toString().contains("private thought"))
        assertFalse(result.toString().contains("secret tool"))
        assertFalse(result.toString().contains("old answer"))
        assertFalse(result.toString().contains("private.png"))
    }

    @Test
    fun `context refuses foreign assistant and missing node`() {
        val convo = conversation(owner = otherAssistant,
            nodes = listOf(MessageNode(id = nodeId, messages = listOf(
                message(MessageRole.USER, UIMessagePart.Text("private"))))))
        assertThrows(NativeMcpInvalidParamsException::class.java) {
            projectHistoryContext(convo, assistant, nodeId, 1, 1)
        }
        assertThrows(NativeMcpInvalidParamsException::class.java) {
            projectHistoryContext(convo.copy(assistantId = assistant), assistant, Uuid.random(), 1, 1)
        }
    }

    @Test
    fun `context has a global text budget and reports truncation`() {
        val huge = message(MessageRole.USER, UIMessagePart.Text("x".repeat(30_000)))
        val convo = conversation(nodes = listOf(MessageNode(id = nodeId, messages = listOf(huge))))
        val result = projectHistoryContext(convo, assistant, nodeId, 0, 0)
        assertEquals("true", result["truncated"]!!.jsonPrimitive.content)
        assertEquals(24_000, result["messages"]!!.jsonArray[0].jsonObjectValue("text").length)
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectValue(key: String): String =
        this.jsonObject[key]!!.jsonPrimitive.content
}
