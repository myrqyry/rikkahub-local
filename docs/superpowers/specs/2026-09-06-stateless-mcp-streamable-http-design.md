# Stateless MCP Streamable HTTP adapter

## Overview

RikkaHub exposes a thin, stateless MCP endpoint at `POST /mcp`. The endpoint
uses Streamable HTTP carrying MCP JSON-RPC messages for protocol revision
`2026-07-28`. It reuses the current assistant's enabled MCP tools and
`McpManager`; it does not create a second backend, session store, or
persistence layer.

This first slice supports ordinary JSON HTTP responses for `tools/list` and
`tools/call`. It does not implement a standalone SSE endpoint, legacy
HTTP+SSE transport, protocol handshake, session IDs, or long-lived streams.

## Scope

The adapter provides these operations:

- `tools/list` returns the current assistant's enabled MCP tools.
- `tools/call` delegates execution to `McpManager.callTool()` after validating
  that the requested tool is currently enabled for the current assistant.

The adapter does not expose MCP server-management operations, resources,
prompts, subscriptions, sampling, elicitation, tasks, or server-initiated
requests.

## Wire contract

Every request is an independent `POST /mcp` request with
`Content-Type: application/json`. The client must advertise both JSON and
request-scoped SSE in `Accept`, even though this slice returns JSON only.

The request must include `MCP-Protocol-Version: 2026-07-28` and
`Mcp-Method`, whose value must match the JSON-RPC `method`. `Mcp-Name` is
required for `tools/call` and must match `params.name`; it is not required for
`tools/list`.

The JSON-RPC request's `params._meta` must contain:

- `io.modelcontextprotocol/protocolVersion` equal to `2026-07-28`.
- `io.modelcontextprotocol/clientInfo` with client `name` and `version`.
- `io.modelcontextprotocol/clientCapabilities` as a JSON object.

The protocol-version body metadata and HTTP header must match. Header or
metadata mismatches return HTTP 400 with a JSON-RPC error using the
MCP-reserved `HeaderMismatch` code. Unsupported methods return HTTP 404 with
JSON-RPC code `-32601`; malformed requests return HTTP 400.

Successful JSON-RPC requests return HTTP 200 and `application/json`. The
response includes a JSON-RPC `result` for success or an `error` object for
execution and validation failures. Notifications are not needed by this
slice.

`tools/list` returns a deterministic, name-sorted list with these result
fields:

```json
{
  "resultType": "complete",
  "tools": [],
  "ttlMs": 0,
  "cacheScope": "private"
}
```

## Tool safety

`McpManager.getAllAvailableTools()` returns the current assistant's enabled
remote MCP tools, not a read-only classification. A tool name cannot prove
that execution is safe. The initial adapter therefore preserves the existing
MCP tool surface and existing RikkaHub tool approval and permission semantics;
it does not silently broaden access or claim that all tools are read-only.

The implementation must document this boundary in the endpoint and test that
only tools returned by the manager can be called. A future restricted
read-only surface requires an explicit capability policy rather than name
matching.

## Security and routing

The adapter runs inside the existing authenticated Ktor route boundary. It
does not change server binding, localhost mode, JWT behavior, or authenticated
LAN mode.

When an `Origin` header is present, the endpoint validates it against the
allowed local origins and returns HTTP 403 for invalid origins. No origin
header is accepted for the first localhost and ADB-forwarding path. The
adapter does not add CORS policy or credentials of its own.

The endpoint ignores any `Mcp-Session-Id`, `Last-Event-ID`, GET, or DELETE
legacy transport behavior. Only the single POST endpoint is implemented.

## Data flow

```text
HTTP POST /mcp
    -> existing Ktor auth and route boundary
    -> stateless request validation
    -> tools/list: McpManager.getAllAvailableTools()
    -> tools/call: validate current tool surface, then McpManager.callTool()
    -> JSON-RPC HTTP response
```

The adapter reads live settings and manager state for every request. It does
not cache tools, retain client sessions, or persist MCP-specific state.

## Testing and acceptance

Focused tests cover request validation, header and `_meta` parity, optional
`Mcp-Name` for `tools/list`, deterministic list output and cache fields,
unknown-tool rejection, and delegation of valid calls. Tests use the real
adapter logic rather than a simulated remote MCP server.

Build checks include the focused app unit tests, `assembleDebug`, web UI
typecheck, and web UI build. Device acceptance installs with `adb install -r`
without clearing data, forwards `tcp:18080` to the configured Android web
server, and exercises `/mcp` through the same localhost path.

The adapter is complete only when an independent HTTP request confirms that a
valid `tools/list` request works without a session or handshake, a valid
`tools/call` reaches the existing manager, and invalid origin, metadata, and
header cases are rejected.

## References

- [MCP Streamable HTTP transport, revision 2026-07-28](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/main/docs/specification/2026-07-28/basic/transports/streamable-http.mdx)
- [MCP revision 2026-07-28 changelog](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/main/docs/specification/2026-07-28/changelog.mdx)
