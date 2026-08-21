# Agent Runtime (ADK) — Agent Orchestration Boundary

## What Lives Here

The Meristem-owned agent-runtime boundary: `AgentRuntime` / `AgentEvent`, a thin
`ChatProviderAdkModel` adapter that lets Google ADK Kotlin drive an existing
Rikkahub `ChatProvider`, and a `SimpleAgentRuntime` wiring one ADK `LlmAgent`
+ `FunctionTool`s into a `Flow<AgentEvent>`. ADK is wrapped, not married — Rikkahub
owns assistants, conversations, providers, tools, and permissions.

## Key Files

| File | Purpose |
|------|---------|
| `src/main/.../AgentRuntime.kt` | `AgentRuntime` interface, `AgentEvent` sealed type, `SimpleAgentRuntime` (ADK `InMemoryRunner`), `Event.toAgentEvent()` mapper |
| `src/main/.../adk/ChatProviderAdkModel.kt` | ADK `Model` adapter around Rikkahub `ChatProvider` (stream/non-stream, system instruction) |
| `build.gradle.kts` | Module dependencies (`com.google.adk:google-adk-kotlin-core:0.8.0`, `:ai`) |

## Deviations from Root

- No deviations — follow root conventions. `consumer-rules.pro` is intentionally empty.

## Dependencies & Side Effects

- `:ai` types carry `@Composable` functions (e.g. `ProviderSetting.description`) — this
  module applies the Compose compiler plugin (`kotlin.compose` + `buildFeatures.compose`)
  so the ABI matches `:ai` (else `NoSuchMethodError` on `ProviderSetting` ctors).
- ADK transitively pulls Apache httpclient/httpcore and google-auth jars that ship
  duplicate `META-INF/DEPENDENCIES` / `META-INF/INDEX.LIST` — `app` excludes those.
- `McpTool` is JVM-only in ADK 0.8.0; keep Rikkahub's Android MCP/tool architecture.
