<!-- meristem-template:v1 -->
# Decisions

Record decisions only when the choice or its rationale is likely to matter later.

Use entries like:

## YYYY-MM-DD — Decision

**Decision:**  
**Why:**  
**Alternatives considered:**  
**Constraints / consequences:**  
**Evidence / references:**  
**Supersedes:** none

## 2026-09-20 — Local System One is the primary fuzzy decision layer

**Decision:**  
Use the reasoning ladder `deterministic -> local System One -> explicitly approved remote System One -> generative`. System-One work is limited to typed boolean, choice, and score judgments. Generative models remain responsible for synthesis.

**Why:**  
RikkaHub already prefers deterministic fast paths and typed agent plans. A small local decision layer fills the gap between exact code and expensive generative reasoning while keeping frequent routing/relevance/rule judgments private and cheap.

**Alternatives considered:**  
Use a full LLM for every fuzzy decision; center hosted Jev as the decision service; make the decision model an authority layer.

**Constraints / consequences:**  
Decision models never grant capabilities or execute tools. Local is primary. Remote decision providers require explicit authorization/data-boundary approval. Confidence is evidence, not authority. The typed contract/routing policy is implemented first; a production local model adapter must be evaluated before it is described as live.

**Evidence / references:**  
`local-llm/src/main/java/me/rerere/locallm/decision/SystemOneDecision.kt`; `docs/references/architecture.md`; shared Meristem skill `system-one-decisions`.

**Supersedes:** none

## 2026-10-05 — Local model selection uses measured execution profiles, not fixed device tiers

**Decision:**
Route Android local-inference selection through a measured `DeviceExecutionProfile` and pure `LiteRtExecutionPlanner`. Live available memory is the hard admission signal; catalog minimum-RAM bands are advisory ranking evidence. Persisted accelerator choices are revalidated against current capabilities before reuse, while native runtime fallback remains the final execution truth.

**Why:**
RikkaHub already had the right ingredients (live `availMem`, accelerator probes, memory admission, model metadata, runtime fallback) but they were independent decisions. A single profile/planner boundary prevents contradictory model/backend choices and gives future quantized variants one place to plug in without copying hardcoded device tiers.

**Alternatives considered:**
Copy Brownie's 6/8/12 GB tiers; select only from total RAM; trust cached accelerator labels indefinitely; implement advertised Q4/Q8 switching without verified matching artifacts.

**Constraints / consequences:**
The planner is evidence/policy, not capability authority. Runtime fallback is reported as degraded execution, not proof the preferred backend worked. LiteRT-LM backend labels must map to real SDK backends; 0.17.0 has no NNAPI backend, so NNAPI remains available only to compute/task adapters that truly support it. Do not claim adaptive quantization until verified Q4/Q8 artifacts exist.

**Evidence / references:**
`local-llm/.../litert/compute/DeviceExecutionProfile.kt`; `local-llm/.../litert/LiteRtExecutionPlanner.kt`; `ExecutionProfileProbe.kt`; `LiteRtRuntime.ensureLoaded`; `docs/references/architecture.md`.

**Supersedes:** none

## 2026-10-07 — Reopen server-owned OpenCode sessions in the Android workspace

**Decision:**
Use the existing OpenCode compatibility HTTP API as RikkaHub's first desktop integration path, instead of inventing a parallel Meristem session server. The remote OpenCode service owns session history. RikkaHub lists recent sessions for its selected remote directory, lets the user explicitly reopen one, and reads the visible text from V2 `{ info, parts }` message envelopes without presenting reasoning/tool parts as chat text. A confirmed prompt is followed by a server transcript refresh, not synthetic local messages; an unconfirmed prompt must not discard the user's draft.

**Why:**
The running Meristem OpenCode server exposes `/project`, `/session`, and `/session/{id}/message`; its V2 message envelope has `info.id` and `info.role`, unlike the Android client's former top-level-only ID assumption. The old parser silently dropped messages even when health checks passed. Reusing its durable session authority avoids another source of truth.

**Constraints / consequences:**
This is a client interoperability improvement, not a completed distributed peer or cross-device continuity protocol. Android remains independently useful and has a separate Ktor/MCP server. Exporting Android device tools to OpenCode requires the existing explicit JWT + device-tool-export opt-ins and stays constrained to the remote-safe allowlist; no automatic discovery, grants, server startup, or public exposure is introduced. Pixel app upgrade and end-to-end UI validation remain outstanding.

**Evidence / references:**
`app/src/main/java/me/rerere/rikkahub/data/opencode/`; `ui/pages/extensions/workspace/OpenCodeWorkspaceDetail{VM,Page}.kt`; the paired `OpenCodeHttpClientTest` and `OpenCodeRepositoryTest`; live read-only OpenCode `:4196` API structure and directory-scoped list probes. Kotlin app compilation passed; focused JUnit tests passed 12/12 under an explicit UTF-8 locale. These tests do not establish a physical Android round trip.

**Supersedes:** none
