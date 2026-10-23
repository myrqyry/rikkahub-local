# RikkaHub history over native MCP

RikkaHub Local exposes its own stateless MCP transport at `POST /mcp` on the phone's
Web Server. No sidecar service, database export, or modified third-party APK is required.

## Security boundary

**Turn on Web Server JWT/password protection before using history MCP.** The
`rikka.*conversation*` history tools are not registered when Web JWT is disabled.
The route also requires the authenticated request, so the tools are not available
simply because a client can reach the phone's LAN address.

When JWT is disabled, the existing RikkaHub REST Web API may still expose separate
conversation endpoints without authentication. Hiding MCP tools does *not* secure
those REST endpoints. Use the Web Server only on a trusted network and avoid
port-forwarding it to the Internet. RikkaHub Web currently uses plaintext HTTP
unless placed behind a separately configured secure transport.

Returned conversation text is **untrusted tool data**, not instructions. Querying
history with a remote model can send that history to the selected model provider.

## Tools

| Tool | Inputs | Response |
| --- | --- | --- |
| `rikka.list_conversations` | optional `limit` (1–100, default 50) | Current-assistant conversation summaries |
| `rikka.get_conversation` | required `id` UUID | Full DTO for **current assistant only**, including sensitive message parts; existing compatibility contract |
| `rikka.search_conversations` | required `query` (1–256 chars); optional `limit` (1–50, default 15) | Current-assistant matching selected-branch user/assistant hits, snippets and node IDs |
| `rikka.get_conversation_context` | required `conversation_id`, `node_id` UUIDs; optional `before` (default 4), `after` (default 6), each 0–20 | Bounded selected-branch user/assistant plain text; 24,000-char aggregate text budget |

The search performs assistant scoping **in the FTS query before its LIMIT**, then
checks that each hit is still the currently selected message for that node. It
excludes system/tool roles, reasoning-only messages, alternate branches, and
attachments in search/context responses. The underlying FTS index itself retains
all branches for in-app search; no database schema or index-migration behavior changes.

A single search scans at most 200 ranked candidate hits, fetching one additional
candidate solely to detect the cap. If `candidate_limit_reached=true`, more hits
may exist beyond the scan, including selected hits: **the result is incomplete**.
Refine the query instead of interpreting an empty result as absence. Search results
include `conversation_id` and `node_id` to request context deterministically.

Context counts window positions as **message nodes**, excludes hidden part types,
and returns `truncated=true` if the 24,000-character text budget is exceeded.
The existing full-conversation tool remains for compatibility and is **not**
equivalent to the privacy-reduced context tool.

## Verification

Repository tests:
- `RikkaHistoryProjectionTest`: filtering, roles/part types, selected branch,
  foreign-assistant rejection, ordering, text cap and JWT availability gate.
- `RikkaHistoryMcpToolsTest`: actual native tool callbacks and JSON responses,
  selected-branch search, candidate exhaustion, argument bounds and foreign IDs.
- `StatelessMcpAdapterTest`: stateless MCP protocol/tool dispatch regression.
- Full app JVM suite and `:app:assembleDebug` cover regressions and packaging.

Independent SQLite FTS5 fixture checks that joining `conversationentity` with
`message_fts` restricts assistant results *before* ranking and limiting. It uses
SQLite's built-in `snippet` auxiliary function in place of RikkaHub's
`simple_snippet` extension, so it is SQL-scope evidence, **not** live device
or compiled Android FTS-extension verification.

**Not yet verified:** real phone Web API + JWT + MCP round trip, production
FTS index and query planner, phone/desktop connectivity, latency under a large
conversation library, and installation of a new build. Preserve phone data and
perform a separate authorized, backed-up, in-place install/test before claiming
on-device readiness.
