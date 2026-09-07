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
private val STANDARD_MCP_PROTOCOL_VERSIONS = setOf("2025-03-26", "2025-06-18", "2025-11-25", MCP_PROTOCOL_VERSION)
private const val JSON_RPC_INVALID_REQUEST = -32600
private const val MCP_HEADER_MISMATCH = -32020
private const val MCP_UNSUPPORTED_VERSION = -32022
private const val MCP_APPROVAL_REQUIRED = -32003

data class StatelessMcpResponse(
    val status: HttpStatusCode,
    val body: JsonObject,
)

class NativeMcpInvalidParamsException(message: String) : IllegalArgumentException(message)

data class NativeMcpTool(
    val name: String,
    val description: String? = null,
    val inputSchema: InputSchema? = null,
    val call: suspend (JsonObject) -> JsonObject,
)

class StatelessMcpAdapter(
    private val availableTools: () -> List<Triple<Uuid, String, McpTool>>,
    private val callTool: suspend (Uuid, String, JsonObject) -> List<UIMessagePart>,
    private val nativeTools: () -> List<NativeMcpTool> = { emptyList() },
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
        val method = body["method"].stringValue()
            ?: return badRequest("method is required")
        if (body["id"] == null && method.startsWith("notifications/")) {
            return StatelessMcpResponse(HttpStatusCode.Accepted, buildJsonObject {})
        }
        if (body["id"] == null) return badRequest("id is required")
        val params = body["params"]?.jsonObject ?: buildJsonObject {}
        val meta = params["_meta"]?.jsonObject
        if (meta == null) {
            val version = params["protocolVersion"].stringValue()
            if (method == "initialize") {
                if (version !in STANDARD_MCP_PROTOCOL_VERSIONS) {
                    return unsupportedProtocolVersion("unsupported protocol version: $version")
                }
                return initialize(body["id"]!!, version!!)
            }
            val headerVersion = headers["MCP-Protocol-Version"]
                ?: return headerMismatch("MCP-Protocol-Version header is required")
            if (headerVersion !in STANDARD_MCP_PROTOCOL_VERSIONS) {
                return unsupportedProtocolVersion("unsupported protocol version: $headerVersion")
            }
            if (headers["Mcp-Method"] != null && headers["Mcp-Method"] != method) {
                return headerMismatch("Mcp-Method must match method")
            }
            return when (method) {
                "tools/list" -> listTools(body["id"]!!)
                "tools/call" -> callTool(body["id"]!!, params, headers, requireNameHeader = false)
                else -> StatelessMcpResponse(HttpStatusCode.NotFound, errorBody(-32601, "Method not found"))
            }
        }
        val bodyVersion = meta["io.modelcontextprotocol/protocolVersion"].stringValue()
            ?: return headerMismatch("protocol version metadata is required")
        meta["io.modelcontextprotocol/clientInfo"]?.let { clientInfoElement ->
            val clientInfo = clientInfoElement.jsonObjectOrNull()
                ?: return badRequest("clientInfo metadata must be an object")
            if (clientInfo["name"].stringValue().isNullOrBlank() ||
                clientInfo["version"].stringValue().isNullOrBlank()
            ) return badRequest("clientInfo name and version are required")
        }
        if (meta["io.modelcontextprotocol/clientCapabilities"] !is JsonObject) {
            return badRequest("clientCapabilities metadata is required")
        }

        val headerVersion = headers["MCP-Protocol-Version"]
            ?: return headerMismatch("MCP-Protocol-Version header is required")
        if (bodyVersion != headerVersion) {
            return headerMismatch("protocol versions must match $MCP_PROTOCOL_VERSION")
        }
        if (headerVersion != MCP_PROTOCOL_VERSION) {
            return unsupportedProtocolVersion("unsupported protocol version: $headerVersion")
        }

        if (headers["Mcp-Method"] != method) return headerMismatch("Mcp-Method must match method")
        if (origin != null && !isAllowedOrigin(origin)) {
            return StatelessMcpResponse(HttpStatusCode.Forbidden, errorBody(JSON_RPC_INVALID_REQUEST, "origin is not allowed"))
        }
        if (method != "tools/list" && method != "tools/call") {
            return StatelessMcpResponse(HttpStatusCode.NotFound, errorBody(-32601, "Method not found"))
        }

        return when (method) {
            "tools/list" -> listTools(body["id"]!!)
            "tools/call" -> callTool(body["id"]!!, params, headers, requireNameHeader = true)
            else -> error("unreachable")
        }
    }

    private fun initialize(id: JsonElement, version: String) = ok(id, buildJsonObject {
        put("protocolVersion", version)
        put("capabilities", buildJsonObject { put("tools", buildJsonObject {}) })
        put("serverInfo", buildJsonObject {
            put("name", "rikkahub")
            put("version", "local")
        })
    })

    private fun listTools(id: JsonElement): StatelessMcpResponse {
        val tools = availableTools().map { (_, _, tool) ->
            toolJson(tool.name, tool.description, tool.inputSchema)
        } + nativeTools().map { tool ->
            toolJson(tool.name, tool.description, tool.inputSchema)
        }
        return ok(id, buildJsonObject {
            put("resultType", "complete")
            put("tools", JsonArray(tools.sortedBy { it["name"].stringValue() }))
            put("ttlMs", 0)
            put("cacheScope", "private")
        })
    }

    private suspend fun callTool(
        id: JsonElement,
        params: JsonObject,
        headers: Map<String, String>,
        requireNameHeader: Boolean,
    ): StatelessMcpResponse {
        val name = params["name"].stringValue()
            ?: return invalidParams(id, "params.name is required")
        if (requireNameHeader && headers["Mcp-Name"] != name) return headerMismatch("Mcp-Name must match params.name")
        val args = params["arguments"]?.jsonObject
            ?: return invalidParams(id, "params.arguments must be an object")
        nativeTools().firstOrNull { it.name == name }?.let { tool ->
            return try {
                val payload = tool.call(args)
                ok(id, jsonContentResult(payload))
            } catch (e: NativeMcpInvalidParamsException) {
                invalidParams(id, e.message ?: "Invalid native tool arguments")
            } catch (e: Exception) {
                StatelessMcpResponse(HttpStatusCode.OK, errorBody(-32000, e.message ?: "Tool execution failed", id))
            }
        }
        val tool = availableTools().firstOrNull { it.third.name == name }
            ?: return invalidParams(id, "Unknown tool: $name")
        if (tool.third.needsApproval) {
            return StatelessMcpResponse(
                HttpStatusCode.OK,
                errorBody(MCP_APPROVAL_REQUIRED, "Tool requires approval", id),
            )
        }
        return try {
            val parts = callTool(tool.first, name, args)
            ok(id, buildJsonObject {
                put("resultType", "complete")
                put("content", JsonArray(parts.mapNotNull(::textContent)))
                put("isError", false)
            })
        } catch (e: Exception) {
            StatelessMcpResponse(HttpStatusCode.OK, errorBody(-32000, e.message ?: "Tool execution failed", id))
        }
    }

    private fun schemaJson(schema: InputSchema): JsonObject = when (schema) {
        is InputSchema.Obj -> buildJsonObject {
            put("type", "object")
            put("properties", schema.properties)
            schema.required?.let { put("required", JsonArray(it.map(::JsonPrimitive))) }
        }
    }

    private fun toolJson(name: String, description: String?, schema: InputSchema?) = buildJsonObject {
        put("name", name)
        description?.let { put("description", it) }
        schema?.let { put("inputSchema", schemaJson(it)) }
    }

    private fun jsonContentResult(payload: JsonObject) = buildJsonObject {
        put("resultType", "complete")
        put("content", JsonArray(listOf(buildJsonObject {
            put("type", "text")
            put("text", payload.toString())
        })))
        put("isError", false)
    }

    private fun textContent(part: UIMessagePart): JsonObject? = (part as? UIMessagePart.Text)?.let {
        buildJsonObject {
            put("type", "text")
            put("text", it.text)
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
        StatelessMcpResponse(HttpStatusCode.OK, errorBody(-32602, message, id))

    private fun badRequest(message: String) =
        StatelessMcpResponse(HttpStatusCode.BadRequest, errorBody(JSON_RPC_INVALID_REQUEST, message))

    private fun headerMismatch(message: String) =
        StatelessMcpResponse(HttpStatusCode.BadRequest, errorBody(MCP_HEADER_MISMATCH, message))

    private fun unsupportedProtocolVersion(message: String) =
        StatelessMcpResponse(HttpStatusCode.BadRequest, errorBody(MCP_UNSUPPORTED_VERSION, message))

    private fun error(message: String): StatelessMcpResponse = badRequest(message)

    private fun errorBody(code: Int, message: String = code.toString(), id: JsonElement? = null) = buildJsonObject {
        put("jsonrpc", "2.0")
        id?.let { put("id", it) }
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
        })
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun isAllowedOrigin(origin: String): Boolean = runCatching {
        val uri = URI(origin)
        uri.scheme in setOf("http", "https") && uri.host in setOf("localhost", "127.0.0.1", "[::1]", "::1")
    }.getOrDefault(false)
}

private fun JsonElement?.stringValue(): String? = (this as? JsonPrimitive)?.content
