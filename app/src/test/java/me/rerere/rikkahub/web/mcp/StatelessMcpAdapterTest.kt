package me.rerere.rikkahub.web.mcp

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.uuid.Uuid

class StatelessMcpAdapterTest {
    private val firstId = Uuid.random()
    private val secondId = Uuid.random()
    private lateinit var adapter: StatelessMcpAdapter
    private var calls = 0

    @Before
    fun setUp() {
        adapter = StatelessMcpAdapter(
            availableTools = {
                listOf(
                    Triple(secondId, "server", McpTool(name = "z-tool", inputSchema = InputSchema.Obj(buildJsonObject {}))),
                    Triple(firstId, "server", McpTool(name = "a-tool", description = "A tool")),
                )
            },
            callTool = { _, _, _ ->
                calls++
                listOf(UIMessagePart.Text("called"))
            },
        )
    }

    @Test
    fun `tools list accepts headers and returns sorted private complete result`() = runBlocking {
        val result = adapter.handle(validHeaders("tools/list"), request(), null)

        assertEquals(HttpStatusCode.OK, result.status)
        val payload = result.body["result"]!!.jsonObject
        assertEquals("complete", payload["resultType"]!!.jsonPrimitive.content)
        assertEquals("private", payload["cacheScope"]!!.jsonPrimitive.content)
        assertEquals(0, payload["ttlMs"]!!.jsonPrimitive.int)
        assertEquals("a-tool", payload["tools"]!!.jsonArray.first().jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tools list does not require Mcp Name`() = runBlocking {
        assertEquals(HttpStatusCode.OK, adapter.handle(validHeaders("tools/list"), request(), null).status)
    }

    @Test
    fun `header and body protocol versions must match`() = runBlocking {
        val result = adapter.handle(validHeaders("tools/list") + ("MCP-Protocol-Version" to "2025-06-18"), request(), null)
        assertEquals(HttpStatusCode.BadRequest, result.status)
        assertEquals("HeaderMismatch", result.body["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `method header must match JSON-RPC method`() = runBlocking {
        assertEquals(HttpStatusCode.BadRequest, adapter.handle(validHeaders("tools/call"), request(), null).status)
    }

    @Test
    fun `unsupported method returns not found`() = runBlocking {
        val result = adapter.handle(validHeaders("resources/list"), request("resources/list"), null)
        assertEquals(HttpStatusCode.NotFound, result.status)
        assertEquals("-32601", result.body["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `invalid origin is forbidden`() = runBlocking {
        assertEquals(HttpStatusCode.Forbidden, adapter.handle(validHeaders("tools/list"), request(), "https://attacker.invalid").status)
    }

    @Test
    fun `valid call delegates to current tool`() = runBlocking {
        val result = adapter.handle(
            validHeaders("tools/call") + ("Mcp-Name" to "a-tool"),
            request("tools/call", buildJsonObject {
                put("_meta", metadata())
                put("name", "a-tool")
                put("arguments", buildJsonObject {})
            }),
            null,
        )
        assertEquals(HttpStatusCode.OK, result.status)
        assertEquals(1, calls)
        assertTrue(result.body["result"]!!.jsonObject["content"]!!.toString().contains("called"))
    }

    private fun validHeaders(method: String) = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json, text/event-stream",
        "MCP-Protocol-Version" to MCP_PROTOCOL_VERSION,
        "Mcp-Method" to method,
    )

    private fun request(method: String = "tools/list", params: kotlinx.serialization.json.JsonObject = buildJsonObject {
        put("_meta", metadata())
    }) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", 1)
        put("method", method)
        put("params", params)
    }

    private fun metadata() = buildJsonObject {
        put("io.modelcontextprotocol/protocolVersion", MCP_PROTOCOL_VERSION)
        put("io.modelcontextprotocol/clientInfo", buildJsonObject {
            put("name", "test-client")
            put("version", "1.0")
        })
        put("io.modelcontextprotocol/clientCapabilities", buildJsonObject {})
    }
}
