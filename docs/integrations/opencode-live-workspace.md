# OpenCode 1 ↔ RikkaHub workspace interaction (2026-10-22)

## Purpose and scope

RikkaHub's existing OpenCode workspace screen now supports real-time OpenCode 1
session monitoring while preserving its existing explicit connection/project
selection and HTTP prompt APIs. This adopts the event/reconnect mechanism
studied in chriswritescode-dev/opencode-manager rather than installing the
Manager app or altering the active OpenCode worktree.

The implementation targets **OpenCode 1's compatibility API** only. The
separate OpenCode 2 migration in `opencode-v2-readiness.md` remains pending,
with no silent fallback, prompt replay or V2 service migration.

## Actual behavior

- Workspace screen lists existing sessions as well as offering "Start session".
  Selecting a prior session reconnects to its ID instead of creating a new one.
- `OpenCodeHttpClient.observeSession` subscribes to
  `GET /global/event` using a cancellable OkHttp SSE connection; the
  stream has its own no-read-timeout HTTP client derived from the existing
  authenticated client. Only recognized events matching the selected session
  and matching workspace (if provided) trigger refresh.
- The SSE parser supports multi-line `data:` frames, OpenCode 1 global
  envelopes and the newer native event layout. It bounds an individual
  frame to 1 MiB and discards malformed/unrelated events.
- SSE connection yields `Connected` and reconnects with capped exponential
  backoff after unplanned EOF/errors; 401/403/unsupported media fail closed.
  Each reconnection triggers an authoritative
  `GET /session/{id}/message`, `GET /session/status`, and
  `GET /permission` snapshot. Changes are coalesced through a single
  pending snapshot task to avoid concurrent conflicting UI writes.
- Permission cards are **server-derived requests**, filtered by exact session
  ID, and show the permission and bounded patterns. They offer only explicit
  **Allow once** and **Reject** (never a permanent Always grant). Before
  `POST /permission/{id}/reply`, the client re-reads pending requests to
  guard stale/foreign IDs, and it only accepts an explicit true server response.
- On a prompt exception, the screen says the outcome is *unconfirmed* and
  asks the user to inspect server history rather than blindly retrying. A
  prompt is never replayed just because SSE reconnects. After success,
  messages are loaded from OpenCode, not appended as potentially duplicate
  local assistant messages.
- Status is read from `/session/status`. When absent or unavailable it is
  **unknown**, not declared idle/completed. SSE transport connectivity is
  displayed separately from observed execution status. Failed permission
  reads clear potentially stale request cards and show a warning.

## Verification evidence

- Test-first work: `OpenCodeLiveBridgeTest` initially failed without
  stream/parser/permission/status APIs. Tests now cover message/session and
  directory isolation, malformed/oversize SSE, selected server-sourced pending
  permissions and single-use reply, status mapping, real OkHttp SSE callback
  delivery, and actual dropped-stream reconnect without any POST.
- Current desktop OpenCode 1 loopback server at `127.0.0.1:4097` reported
  `1.18.31` on `GET /global/health`. Read-only requests to
  `/session/status`, `/permission` and `/session` returned HTTP 200,
  with empty status/permission collections at inspection. A 3-second
  read-only SSE handshake returned HTTP 200, `Content-Type:
  text/event-stream`, and an initial `server.connected` event. That
  verifies only the **existing desktop endpoint**, not a phone connection.
- Final `./gradlew :app:testDebugUnitTest :app:assembleDebug` **PASS**:
  1,560 app JVM tests in 229 suites, 1 intentional opt-in skip, zero
  failures/errors, with debug APK assembled. This verifies Kotlin app
  regressions and packaging, **not** real Android UI behavior.

## Deliberate limits and unresolved gates

- **No user-data-bearing phone APK installed**, no app-data reset,
  server restart, active OpenCode session mutation, permission grant, or new
  external listener. Existing OpenCode UI worktree changes were untouched.
- **Correction (2026-10-22):** Port 4097 is only one loopback OpenCode
  process, not the user's sole transport. A separate OpenCode-compatible
  server is listening on **0.0.0.0:4196**, and `doopa` (another tailnet
  device) successfully performed read-only GETs over the desktop Tailscale
  IP to `/global/health`, `/session/status`, and `/permission`: all
  HTTP 200. A cross-device `/global/event` SSE connection returned HTTP
  200, `text/event-stream`, and `server.connected`. Thus **tailnet
  transport already exists**; setting up a new proxy is not an established
  prerequisite. This was a read-only peer test, **not phone-originated
  validation**.
- Port 4196 is reached as a direct tailnet-IP TCP listener, **not** one
  of the current `tailscale serve` HTTPS proxy mappings. These read-only
  requests received no OpenCode HTTP auth challenge from a permitted tailnet
  peer. Before enabling remote approval actions, review tailnet ACLs and
  endpoint auth and verify whether the 0.0.0.0 bind also allows unintended
  local-network reachability. Do not expose the raw coding API publicly.
- No real pending permission existed at inspection, so the button-to-server
  action and on-device permission approval are not device verified.
  Approval is interactive while the workspace screen is open; background
  notifications, OS lifecycle persistence, question/form handoff, and cross
  project activity dashboards are **not** implemented.
- This is workspace session interaction, distinct from the existing
  RikkaHub ↔ OpenCode model inference bridges and the history MCP server.
  Do not conflate working local SSE/read-only calls with remote-phone
  acceptance. After endpoint auth/ACL review and a backed-up in-place APK
  update, verify actual phone-originated events, disconnect/reconnect, approvals,
  cancellation, status and no duplicate prompts.
