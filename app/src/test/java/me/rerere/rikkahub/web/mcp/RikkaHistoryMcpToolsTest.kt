package me.rerere.rikkahub.web.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.uuid.Uuid

class RikkaHistoryMcpToolsTest {
    private val assistant = Uuid.random()
    private val other = Uuid.random()
    private val selected = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("needle present")))
    private val stale = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("unselected needle")))
    private val node = MessageNode(messages = listOf(stale, selected), selectIndex = 1)
    private val conversation = Conversation(
        assistantId = assistant, title = "Example", messageNodes = listOf(node)
    )
    private val foreign = Conversation(
        assistantId = other, title = "Foreign", messageNodes = listOf(node)
    )

    private class FakeHistory(
        val selectedAssistant: Uuid,
        val conversations: Map<Uuid, Conversation>,
        var hits: List<MessageSearchResult> = emptyList(),
    ) : RikkaHistoryDataSource {
        var lastLimit = 0
        override suspend fun currentAssistantId(): Uuid = selectedAssistant
        override suspend fun listConversations(assistantId: Uuid): List<Conversation> =
            conversations.values.filter { it.assistantId == assistantId }
        override suspend fun getConversation(id: Uuid): Conversation? = conversations[id]
        override suspend fun search(query: String, assistantId: Uuid, maxCandidates: Int): List<MessageSearchResult> {
            assertEquals(selectedAssistant, assistantId)
            assertEquals("needle", query)
            lastLimit = maxCandidates
            return hits
        }
    }

    private fun result(tool: NativeMcpTool, args: kotlinx.serialization.json.JsonObject) =
        Json.parseToJsonElement((runBlocking { tool.call(args) }.single() as UIMessagePart.Text).text).jsonObject

    @Test
    fun `native tools export exact history methods and input schemas`() {
        val tools = RikkaConversationMcpTools(FakeHistory(assistant, emptyMap())).tools()
        assertEquals(
            listOf("rikka.list_conversations", "rikka.get_conversation",
                "rikka.search_conversations", "rikka.get_conversation_context"),
            tools.map { it.name },
        )
        assertTrue(tools.all { it.inputSchema != null })
    }

    @Test
    fun `MCP search checks selected branch and serializes only matching assistant records`() {
        val fake = FakeHistory(assistant, mapOf(conversation.id to conversation, foreign.id to foreign))
        fun hit(c: Conversation, m: UIMessage) =
            MessageSearchResult(node.id.toString(), m.id.toString(), c.id.toString(),
                c.title, Instant.parse("2026-10-21T00:00:00Z"), "needle")
        fake.hits = listOf(hit(foreign, selected), hit(conversation, stale), hit(conversation, selected))
        val tool = RikkaConversationMcpTools(fake).tools().first { it.name == "rikka.search_conversations" }
        val json = result(tool, buildJsonObject { put("query", "needle"); put("limit", 1) })
        assertEquals(201, fake.lastLimit)
        assertEquals(1, json["items"]!!.jsonArray.size)
        assertEquals(conversation.id.toString(),
            json["items"]!!.jsonArray[0].jsonObject["conversation_id"]!!.jsonPrimitive.content)
        assertFalse(json.toString().contains("Foreign"))
        assertTrue(json["security_notice"]!!.jsonPrimitive.content.contains("untrusted"))
    }

    @Test
    fun `MCP search exposes candidate exhaustion even when it returns no visible matches`() {
        val fake = FakeHistory(assistant, mapOf(conversation.id to conversation))
        val hit = MessageSearchResult(node.id.toString(), stale.id.toString(),
            conversation.id.toString(), "Example", Instant.EPOCH, "stale")
        fake.hits = List(201) { hit }
        val tool = RikkaConversationMcpTools(fake).tools().first { it.name == "rikka.search_conversations" }
        val json = result(tool, buildJsonObject { put("query", "needle") })
        assertEquals("true", json["candidate_limit_reached"]!!.jsonPrimitive.content)
        assertEquals(0, json["items"]!!.jsonArray.size)
    }

    @Test
    fun `MCP context cannot access a different assistant and excludes tool information`() {
        val fake = FakeHistory(assistant, mapOf(conversation.id to conversation, foreign.id to foreign))
        val tool = RikkaConversationMcpTools(fake).tools().first { it.name == "rikka.get_conversation_context" }
        val good = result(tool, buildJsonObject {
            put("conversation_id", conversation.id.toString())
            put("node_id", node.id.toString())
        })
        assertEquals("needle present", good["messages"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertFalse(good.toString().contains("unselected"))
        try {
            result(tool, buildJsonObject {
                put("conversation_id", foreign.id.toString())
                put("node_id", node.id.toString())
            })
            throw AssertionError("Other assistant data was exposed")
        } catch (expected: NativeMcpInvalidParamsException) {
            assertTrue(expected.message!!.contains("current assistant"))
        }
    }

    @Test
    fun `existing full-conversation tool rejects foreign assistant IDs`() {
        val fake = FakeHistory(assistant, mapOf(conversation.id to conversation, foreign.id to foreign))
        val tools = RikkaConversationMcpTools(fake).tools()
        val list = result(tools.first { it.name == "rikka.list_conversations" }, buildJsonObject {})
        assertEquals(1, list["conversations"]!!.jsonArray.size)
        val get = tools.first { it.name == "rikka.get_conversation" }
        try {
            result(get, buildJsonObject { put("id", foreign.id.toString()) })
            throw AssertionError("Foreign conversation was exposed")
        } catch (expected: NativeMcpInvalidParamsException) {
            assertTrue(expected.message!!.contains("current assistant"))
        }
    }

    @Test
    fun `MCP history tools reject invalid argument bounds`() {
        val tool = RikkaConversationMcpTools(FakeHistory(assistant, emptyMap())).tools()
            .first { it.name == "rikka.search_conversations" }
        for (args in listOf(
            buildJsonObject { put("query", " ") },
            buildJsonObject { put("query", "x".repeat(257)) },
            buildJsonObject { put("query", "needle"); put("limit", 51) },
            buildJsonObject { put("query", "needle"); put("limit", "bad") },
        )) {
            try {
                result(tool, args)
                throw AssertionError("Invalid args accepted: $args")
            } catch (_: NativeMcpInvalidParamsException) { }
        }
    }
}
