package me.rerere.rikkahub.web.mcp

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpTool
import java.net.URI
import kotlin.uuid.Uuid

const val MCP_PROTOCOL_VERSION = "2026-07-28"

data class StatelessMcpResponse(
    val status: HttpStatusCode,
    val body: JsonObject,
)

class StatelessMcpAdapter(
    private val availableTools: () -> List<Triple<Uuid, String, McpTool>>,
    private val callTool: suspend (Uuid, String, JsonObject) -> List<UIMessagePart>,
) {
    suspend fun handle(
        headers: Map<String, String>,
        body: JsonObject,
        origin: String?,
    ): StatelessMcpResponse {
        val contentType = headers["Content-Type"]?.substringBefore(';')?.trim()
        if (contentType != "application/json") return badRequest("Content-Type must be application/json")

        val accept = headers["Accept"].orEmpty().split(',').map { it.substringBefore(';').trim() }
        if ("application/json" !in accept || "text/event-stream" !in accept) {
            return badRequest("Accept must include application/json and text/event-stream")
        }

        if (body["jsonrpc"].stringValue() != "2.0") return badRequest("jsonrpc must be 2.0")
        if (body["id"] == null) return badRequest("id is required")
        val method = body["method"].stringValue()
            ?: return badRequest("method is required")
        val params = body["params"]?.jsonObject ?: return badRequest("params must be an object")
        val meta = params["_meta"]?.jsonObject ?: return badRequest("params._meta is required")
        val bodyVersion = meta["io.modelcontextprotocol/protocolVersion"].stringValue()
            ?: return headerMismatch("protocol version metadata is required")
        val clientInfo = meta["io.modelcontextprotocol/clientInfo"]?.jsonObject
            ?: return badRequest("clientInfo metadata is required")
        if (clientInfo["name"].stringValue().isNullOrBlank() ||
            clientInfo["version"].stringValue().isNullOrBlank()
        ) return badRequest("clientInfo name and version are required")
        if (meta["io.modelcontextprotocol/clientCapabilities"] !is JsonObject) {
            return badRequest("clientCapabilities metadata is required")
        }

        val headerVersion = headers["MCP-Protocol-Version"]
            ?: return headerMismatch("MCP-Protocol-Version header is required")
        if (headerVersion != MCP_PROTOCOL_VERSION || bodyVersion != headerVersion) {
            return headerMismatch("protocol versions must match $MCP_PROTOCOL_VERSION")
        }

        if (headers["Mcp-Method"] != method) return badRequest("Mcp-Method must match method")
        if (origin != null && !isAllowedOrigin(origin)) {
            return StatelessMcpResponse(HttpStatusCode.Forbidden, errorBody("origin is not allowed"))
        }
        if (method != "tools/list" && method != "tools/call") {
            return StatelessMcpResponse(HttpStatusCode.NotFound, errorBody("-32601", "Method not found"))
        }

        return when (method) {
            "tools/list" -> listTools(body["id"]!!)
            "tools/call" -> callTool(body["id"]!!, params, headers)
            else -> error("unreachable")
        }
    }

    private fun listTools(id: JsonElement): StatelessMcpResponse {
        val tools = availableTools().sortedBy { it.third.name }.map { (_, _, tool) ->
            buildJsonObject {
                put("name", tool.name)
                tool.description?.let { put("description", it) }
                tool.inputSchema?.let { put("inputSchema", schemaJson(it)) }
            }
        }
        return ok(id, buildJsonObject {
            put("resultType", "complete")
            put("tools", JsonArray(tools))
            put("ttlMs", 0)
            put("cacheScope", "private")
        })
    }

    private suspend fun callTool(
        id: JsonElement,
        params: JsonObject,
        headers: Map<String, String>,
    ): StatelessMcpResponse {
        val name = params["name"].stringValue()
            ?: return invalidParams(id, "params.name is required")
        if (headers["Mcp-Name"] != name) return badRequest("Mcp-Name must match params.name")
        val args = params["arguments"]?.jsonObject
            ?: return invalidParams(id, "params.arguments must be an object")
        val tool = availableTools().firstOrNull { it.third.name == name }
            ?: return invalidParams(id, "Unknown tool: $name")
        return try {
            val parts = callTool(tool.first, name, args)
            ok(id, buildJsonObject {
                put("content", JsonArray(parts.mapNotNull { part ->
                    (part as? UIMessagePart.Text)?.let {
                        buildJsonObject {
                            put("type", "text")
                            put("text", it.text)
                        }
                    }
                }))
                put("isError", false)
            })
        } catch (e: Exception) {
            StatelessMcpResponse(HttpStatusCode.OK, errorBody("-32000", e.message ?: "Tool execution failed", id))
        }
    }

    private fun schemaJson(schema: InputSchema): JsonObject = when (schema) {
        is InputSchema.Obj -> buildJsonObject {
            put("type", "object")
            put("properties", schema.properties)
            schema.required?.let { put("required", JsonArray(it.map(::JsonPrimitive))) }
        }
    }

    private fun ok(id: JsonElement, result: JsonObject) = StatelessMcpResponse(
        HttpStatusCode.OK,
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        },
    )

    private fun invalidParams(id: JsonElement, message: String) =
        StatelessMcpResponse(HttpStatusCode.OK, errorBody("-32602", message, id))

    private fun badRequest(message: String) =
        StatelessMcpResponse(HttpStatusCode.BadRequest, errorBody(message))

    private fun headerMismatch(message: String) =
        StatelessMcpResponse(HttpStatusCode.BadRequest, errorBody("HeaderMismatch", message))

    private fun error(message: String): StatelessMcpResponse = badRequest(message)

    private fun errorBody(code: String, message: String = code, id: JsonElement? = null) = buildJsonObject {
        put("jsonrpc", "2.0")
        id?.let { put("id", it) }
        put("error", buildJsonObject {
            code.toIntOrNull()?.let { put("code", it) } ?: put("code", code)
            put("message", message)
        })
    }

    private fun isAllowedOrigin(origin: String): Boolean = runCatching {
        val uri = URI(origin)
        uri.scheme in setOf("http", "https") && uri.host in setOf("localhost", "127.0.0.1", "[::1]", "::1")
    }.getOrDefault(false)
}

private fun JsonElement?.stringValue(): String? = (this as? JsonPrimitive)?.content
