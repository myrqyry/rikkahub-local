package me.rerere.rikkahub.web.mcp

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.ai.mcp.McpManager

fun Route.statelessMcpRoute(mcpManager: McpManager) {
    route("/mcp") {
        post {
            val body = runCatching { call.receive<JsonObject>() }.getOrElse {
                call.respond(
                    HttpStatusCode.BadRequest,
                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("error", buildJsonObject {
                            put("code", -32600)
                            put("message", "Request body must be a JSON object")
                        })
                    },
                )
                return@post
            }
            val headers = buildMap {
                put("Content-Type", call.request.headers[HttpHeaders.ContentType].orEmpty())
                put("Accept", call.request.headers[HttpHeaders.Accept].orEmpty())
                call.request.headers["MCP-Protocol-Version"]?.let { put("MCP-Protocol-Version", it) }
                call.request.headers["Mcp-Method"]?.let { put("Mcp-Method", it) }
                call.request.headers["Mcp-Name"]?.let { put("Mcp-Name", it) }
            }
            val response = StatelessMcpAdapter(
                availableTools = mcpManager::getAllAvailableTools,
                callTool = mcpManager::callTool,
            ).handle(headers, body, call.request.headers[HttpHeaders.Origin])
            call.respond(response.status, response.body)
        }
    }
}
