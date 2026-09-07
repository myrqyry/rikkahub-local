# Stateless MCP Streamable HTTP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an authenticated, stateless `POST /mcp` Streamable HTTP JSON-RPC adapter for the current assistant's MCP tools.

**Architecture:** Keep protocol parsing and validation in a small pure Kotlin adapter that returns typed JSON-RPC envelopes. Mount one root Ktor `POST /mcp` route inside the existing authentication decision, pass the existing `McpManager` through `WebApiModule` and `WebServerManager`, and delegate valid calls without introducing sessions, caches, or a second permission system.

**Tech Stack:** Kotlin 2.4.0, kotlinx.serialization JSON, Ktor server routing/content negotiation, existing `McpManager` from `io.modelcontextprotocol:kotlin-sdk:0.14.0`, JUnit 4 app unit tests, adb acceptance.

## Global Constraints

- Protocol revision is exactly `2026-07-28`.
- Transport is `POST` only with JSON responses; do not add legacy HTTP+SSE, handshake, session IDs, GET, or DELETE behavior.
- Requests require `Content-Type: application/json` and an `Accept` value containing both `application/json` and `text/event-stream`.
- Requests require `MCP-Protocol-Version`, `Mcp-Method`, and `_meta` values for protocol version, client info, and client capabilities.
- Header/body and method/name mismatches return HTTP 400; use the MCP-reserved `HeaderMismatch` error code for header or metadata parity failures.
- Unsupported methods return HTTP 404 with JSON-RPC `-32601`.
- `tools/list` is live, deterministic by tool name, and includes `resultType: complete`, `ttlMs: 0`, and `cacheScope: private`.
- `tools/call` may invoke any tool currently exposed by `McpManager`; do not assume tool names imply read-only behavior or bypass existing approval and permission semantics.
- Validate an `Origin` header when present and return HTTP 403 for an invalid origin; do not add credentials or CORS behavior.
- Preserve installed app data; device installs use `adb install -r` only.
- Use the smallest focused diff and ASCII source unless the existing file requires otherwise.

---

### Task 1: Add pure MCP request and response adapter

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/web/mcp/StatelessMcpAdapter.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/web/mcp/StatelessMcpAdapterTest.kt`

**Interfaces:**
- Consumes: request headers, JSON-RPC body, `McpManager.getAllAvailableTools()`, and `McpManager.callTool(serverId, toolName, args)`.
- Produces: `suspend fun handle(headers: Map<String, String>, body: JsonObject, origin: String?): StatelessMcpResponse` and serializable response envelopes used by the Ktor route.

- [ ] **Step 1: Write failing unit tests for protocol validation and list output**

```kotlin
private const val VERSION = "2026-07-28"

private fun request(
    method: String = "tools/list",
    params: JsonObject = buildJsonObject {
        put("_meta", buildJsonObject {
            put("io.modelcontextprotocol/protocolVersion", VERSION)
            put("io.modelcontextprotocol/clientInfo", buildJsonObject {
                put("name", "test-client")
                put("version", "1.0")
            })
            put("io.modelcontextprotocol/clientCapabilities", buildJsonObject {})
        })
    },
): JsonObject = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", 1)
    put("method", method)
    put("params", params)
}

@Test
fun `tools list accepts headers and returns sorted private complete result`() = runTest {
    val result = adapter.handle(
        headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json, text/event-stream",
            "MCP-Protocol-Version" to VERSION,
            "Mcp-Method" to "tools/list",
        ),
        body = request(),
        origin = null,
    )

    assertEquals(HttpStatusCode.OK, result.status)
    assertEquals("complete", result.body["result"]!!.jsonObject["resultType"]!!.jsonPrimitive.content)
    assertEquals("private", result.body["result"]!!.jsonObject["cacheScope"]!!.jsonPrimitive.content)
    assertEquals(0, result.body["result"]!!.jsonObject["ttlMs"]!!.jsonPrimitive.int)
}

@Test
fun `tools list does not require Mcp Name`() = runTest {
    val result = adapter.handle(validHeaders, request(), origin = null)
    assertEquals(HttpStatusCode.OK, result.status)
}

@Test
fun `header and body protocol versions must match`() = runTest {
    val result = adapter.handle(validHeaders + ("MCP-Protocol-Version" to "2025-06-18"), request(), null)
    assertEquals(HttpStatusCode.BadRequest, result.status)
    assertEquals("HeaderMismatch", result.body["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
}

@Test
fun `method header must match JSON-RPC method`() = runTest {
    val result = adapter.handle(validHeaders + ("Mcp-Method" to "tools/call"), request(), null)
    assertEquals(HttpStatusCode.BadRequest, result.status)
}

@Test
fun `unsupported method returns not found`() = runTest {
    val result = adapter.handle(validHeaders + ("Mcp-Method" to "resources/list"), request("resources/list"), null)
    assertEquals(HttpStatusCode.NotFound, result.status)
    assertEquals("-32601", result.body["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
}

@Test
fun `invalid origin is forbidden`() = runTest {
    val result = adapter.handle(validHeaders, request(), origin = "https://attacker.invalid")
    assertEquals(HttpStatusCode.Forbidden, result.status)
}
```

Use a small fake manager or a constructor-injected function seam in the adapter test; the fake returns two tools in reverse order and records calls. Do not mock a remote MCP server.

- [ ] **Step 2: Run the focused tests and verify they fail for missing adapter types**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.web.mcp.StatelessMcpAdapterTest'`

Expected: FAIL because `StatelessMcpAdapter` and its response types do not exist yet.

- [ ] **Step 3: Implement the minimal serializable envelopes and validation**

Define `StatelessMcpResponse(status: HttpStatusCode, body: JsonObject)` and a manager-facing interface or lambdas so unit tests do not construct Android/Koin state. Validate, in order: content type, combined accept, JSON-RPC version/id/method/params, required `_meta` object and exact metadata keys, required headers, header/body version parity, method parity, origin, and method-specific `Mcp-Name` parity. Return JSON-RPC errors while preserving the required HTTP status.

For `tools/list`, map `Triple<Uuid, String, McpTool>` to `{name, description, inputSchema}` and sort by `name`, then return:

```kotlin
buildJsonObject {
    put("resultType", "complete")
    put("tools", JsonArray(tools))
    put("ttlMs", 0)
    put("cacheScope", "private")
}
```

For `tools/call`, require `params.name`, require matching `Mcp-Name`, require `params.arguments` to be a JSON object, find the current enabled tool by exact name, then call the manager with its server UUID and return text parts as MCP content items. Unknown tools return JSON-RPC `-32602`; manager failures return a JSON-RPC `-32000` error without changing existing manager behavior.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.web.mcp.StatelessMcpAdapterTest'`

Expected: PASS for validation, deterministic list metadata, unknown-tool rejection, origin rejection, and valid call delegation.

- [ ] **Step 5: Commit the adapter unit**

```bash
git add app/src/main/java/me/rerere/rikkahub/web/mcp/StatelessMcpAdapter.kt app/src/test/java/me/rerere/rikkahub/web/mcp/StatelessMcpAdapterTest.kt
git commit -m "feat: add stateless MCP request adapter"
```

### Task 2: Mount the adapter in the authenticated web API

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/web/WebApiModule.kt:61-187`
- Modify: `app/src/main/java/me/rerere/rikkahub/web/WebServerManager.kt:140-142`
- Test: `app/src/test/java/me/rerere/rikkahub/web/mcp/StatelessMcpRouteTest.kt`

**Interfaces:**
- Consumes: `StatelessMcpAdapter`, `McpManager`, existing `configureWebApi` arguments, and Ktor request/response APIs.
- Produces: authenticated `POST /mcp` with JSON response behavior and no GET/DELETE route.

- [ ] **Step 1: Write failing Ktor route tests**

```kotlin
@Test
fun `post mcp delegates tools list`() = testApplication {
    application { configureTestWebApi(fakeMcpManager) }
    val response = client.post("/mcp") {
        contentType(ContentType.Application.Json)
        accept(ContentType.Application.Json, ContentType.Text.EventStream)
        header("MCP-Protocol-Version", VERSION)
        header("Mcp-Method", "tools/list")
        setBody(validListBody())
    }
    assertEquals(HttpStatusCode.OK, response.status)
}

@Test
fun `mcp route has no legacy get or delete behavior`() = testApplication {
    application { configureTestWebApi(fakeMcpManager) }
    assertEquals(HttpStatusCode.NotFound, client.get("/mcp").status)
    assertEquals(HttpStatusCode.NotFound, client.delete("/mcp").status)
}
```

- [ ] **Step 2: Run the route tests and verify they fail before wiring**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.web.mcp.StatelessMcpRouteTest'`

Expected: FAIL because `/mcp` is not mounted and `configureWebApi` has no manager parameter.

- [ ] **Step 3: Wire `McpManager` through the existing server startup**

Add `mcpManager: McpManager` to `configureWebApi`, pass it from `WebServerManager` using its existing injected Koin dependency, and mount `statelessMcpRoute(mcpManager)` at `/mcp` in both the JWT-authenticated and unauthenticated branches. Keep the route inside the existing authentication decision; do not add a second authentication path.

The route must call `call.receive<JsonObject>()`, construct the header map from the exact required headers, pass the optional `Origin`, and respond with the adapter's HTTP status and JSON body. Ktor's existing `ContentNegotiation` supplies `application/json`; the route must not create an SSE connection.

- [ ] **Step 4: Run route tests and verify authentication boundaries**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.web.mcp.StatelessMcpRouteTest'`

Expected: PASS for valid POST, no GET/DELETE route, and the existing JWT-enabled configuration rejecting unauthenticated POST requests.

- [ ] **Step 5: Commit the route integration**

```bash
git add app/src/main/java/me/rerere/rikkahub/web/WebApiModule.kt app/src/main/java/me/rerere/rikkahub/web/WebServerManager.kt app/src/test/java/me/rerere/rikkahub/web/mcp/StatelessMcpRouteTest.kt
git commit -m "feat: expose authenticated MCP HTTP route"
```

### Task 3: Verify delivery on build and device

**Files:**
- Modify: only files required to fix verified compile/test failures from Tasks 1-2.

**Interfaces:**
- Consumes: committed adapter and route behavior.
- Produces: build evidence and localhost/ADB-forwarded acceptance evidence; no new API surface.

- [ ] **Step 1: Run focused app tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'me.rerere.rikkahub.web.mcp.*'`

Expected: PASS with no new warnings that affect MCP behavior.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install without clearing application data and forward the web port**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Run: `adb forward tcp:18080 tcp:8080`

Expected: install succeeds while preserving the existing package and forward is listed by `adb forward --list`.

- [ ] **Step 4: Exercise `tools/list` through the forwarded endpoint**

Run an HTTP POST to `http://127.0.0.1:18080/mcp` with `Content-Type: application/json`, `Accept: application/json, text/event-stream`, `MCP-Protocol-Version: 2026-07-28`, `Mcp-Method: tools/list`, and a valid `_meta` object. Confirm HTTP 200, sorted tool names, `resultType=complete`, `ttlMs=0`, and `cacheScope=private`.

- [ ] **Step 5: Exercise rejection paths and a valid call**

Send requests with mismatched protocol metadata, invalid `Origin`, missing `Mcp-Name` for `tools/call`, and an unknown tool. Confirm HTTP 400/403 and JSON-RPC error bodies. Send one currently listed tool call only if its existing approval/permission state allows it; confirm the manager path is reached without bypassing approval.

- [ ] **Step 6: Run the repository verification commands**

Run: `./gradlew test`

Run: `cd web-ui && pnpm run typecheck`

Run: `cd web-ui && pnpm run build`

Expected: all commands pass; report existing web sourcemap/chunk warnings separately rather than treating them as MCP failures.

- [ ] **Step 7: Inspect the final diff and commit verification fixes**

Run: `git status --short`

Run: `git --no-pager diff --check`

Run: `git --no-pager log -5 --oneline`

If Task 3 required source fixes in `StatelessMcpAdapter.kt`, `StatelessMcpRoute.kt`, `WebApiModule.kt`, or `WebServerManager.kt`, commit only the changed files:

```bash
git add app/src/main/java/me/rerere/rikkahub/web/mcp/StatelessMcpAdapter.kt app/src/main/java/me/rerere/rikkahub/web/mcp/StatelessMcpRoute.kt app/src/main/java/me/rerere/rikkahub/web/WebApiModule.kt app/src/main/java/me/rerere/rikkahub/web/WebServerManager.kt
git commit -m "test: verify stateless MCP delivery"
```

## Self-Review

- Spec coverage: Tasks 1 and 2 cover the wire contract, statelessness, live manager state, tool-call delegation, route placement, and security boundaries. Task 3 covers localhost/ADB acceptance and required repository checks.
- Placeholder scan: no implementation step is deferred to an unspecified TODO; test bodies identify concrete request fields and expected statuses. The test helper names are local test fixtures and must be defined in the test file before running.
- Type consistency: `StatelessMcpResponse` carries `HttpStatusCode` and `JsonObject`; the route responds with those exact values. The manager call signature is `suspend fun callTool(serverId: Uuid, toolName: String, args: JsonObject): List<UIMessagePart>`, and available tools are `List<Triple<Uuid, String, McpTool>>`.
- Intentional boundary: the first implementation does not classify tools as read-only because `McpManager` exposes enabled tools rather than safety metadata. Existing manager approval and permission behavior remains authoritative.
