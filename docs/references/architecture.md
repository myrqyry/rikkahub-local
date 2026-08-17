# RikkaHub Canonical Architecture

> Baseline frozen at `master@6fd0cf6` (all 10 original roadmap phases landed).

This document is the single canonical statement of RikkaHub's agentic architecture.
It supersedes earlier whiteboard plans and per-phase notes. If a subsystem description
conflicts with this file, this file wins.

## The invariant

> **Models propose. Deterministic layers compile, simulate and verify. The capability
> broker authorizes. Executors act. Receipts prove what happened. Humans and agents each
> receive the projection appropriate to them.**

Composition layers (planner, verifier, specialist, workflow) request capabilities; they
**never own authority**. Authority lives only in the capability broker. This is a hard
line and is not negotiable.

## Layered flow

```text
                         USER / AGENT
                              │
                              ▼
                       Intent / Router
                              │
                              ▼
                         ActionPlan
                              │
                    deterministic compile
                              │
                   Shadow Candidate Eval
                              │
                              ▼
                    CAPABILITY / EFFECT BROKER
                              │
     ┌────────────┬───────────┼──────────┬──────────┬───────────┐
     ▼            ▼           ▼          ▼          ▼           ▼
 Direct Tool     Zero      Workspace   Terminal   Browser   Generation
                  │           │          │          │           │
             Procedure   Shadow/Real     PTY      semantic   multimodal
                  │       Local/SSH                snapshots      │
                  │                                              ▼
                  │                                          ArtifactRef
                  │
                  ├──────────────────────────────┐
                  ▼                              ▼
             Compute                         ServiceWorld
        LiteRT / CPU / GPU               Simulated / Emulated / Real
                  │                              │
                  └──────────────┬───────────────┘
                                 ▼
                         WorkflowReceipt
                                 │
                         AgentRun Event Trace
                                 │
                 ┌───────────────┴────────────────┐
                 ▼                                ▼
             RikkaUI                    semantic agent state
          human projection               machine projection
```

The **MicroAgentEventMesh** is communication, not authority. Planner / verifier /
specialist events flow through it, but any capability request still goes through the
same capability broker.

## Core concepts

### ActionPlan (typed intent)

The narrow, typed output of the intent/router layer. The model never executes tools
directly; it emits an `ActionPlan` that the deterministic layer compiles.

- `ActionPlan.ToolCall(toolName, args, grant)` — a single tool invocation.
- `ActionPlan.WorkflowCall(workflowId, inputs, grant)` — an existing Rikka workflow.
- `ActionPlan.ProcedureCall(procedureId, inputs, grant)` — a Zero procedure (future).
- `CapabilityGrant(requested, granted, rejected)` — what this request was allowed to do.

### Deterministic compilation

`ActionPlanCompiler` structurally and semantically validates a plan and applies
**lossless** repairs automatically (canonical aliases, default filling, type coercion,
dropping undeclared args). Lossy/semantic repairs are never silently applied. Only after
deterministic repair does the system consider LLM repair, rejection, or execution.

### Shadow candidate evaluation

`ShadowCandidateEvaluator` scores alternate candidate plans (alias-normalized,
default-filled, coerced, LLM-repair, cached/minned procedure) **without executing any
tool**. Score combines compile validity with capability coverage. Deterministic
tie-break. Invalid candidates cannot win unless every candidate is invalid.

### Capability / effect broker

The single authority. Consumes the granted capabilities and the typed effect/resource
scopes beneath them, and produces an authorization decision for each side-effect path.
Every side-effect path must have a typed plan, a capability/effect decision, a resource
budget, and a receipt.

### Executors

- **DirectToolExecutor** — simple, single-tool calls.
- **ZeroProcedureEngine** — deterministic DAG of procedure steps (compiled references,
  topological order, template resolution, timeouts, fail-fast). Procedures never carry
  authority; they request capabilities through the broker.

### Receipts

`WorkflowReceipt` records what actually happened (compile outcome, repairs, diagnostics,
granted capabilities, status, error, duration). Receipts are routed into the append-only
`AgentRun` event trace so a human or agent can replay what occurred.

### RikkaUI (human projection)

Generated UI is a **typed** `UIMessagePart.GeneratedUi`, not JSON smuggled through text.
Compose is the primary renderer. It renders the human projection of agent state.
Semantic agent state is the machine projection.

### MicroAgentEventMesh

Deterministic in-process pub/sub for planner/verifier/specialist communication. Topic
routing, stable subscriber order, exception isolation. Purely communication — no
authority. App-side sink persists important events into the AgentRun trace.

## Module boundaries

Seams (pure, deterministic, JVM-testable) live in `local-llm` / `ai` and never depend on
the app. App-side implementations adapt them and are wired via DI (Koin):

| Seam (local-llm)               | App implementation            |
| ------------------------------ | ----------------------------- |
| `ZeroWorkflowExecutor`         | `WorkflowEngineZeroWorkflowExecutor` |
| `WorkflowReceiptSink`          | `AgentRunWorkflowReceiptSink` |
| `CapabilityGrantSource`        | `ToolApprovalCapabilityGrantSource` |
| `ProcedureCache`               | (pending Room-backed repo)    |
| `MicroAgentEventSink`          | (pending AgentRun trace sink) |

## Subsystem status

| Area                       | Status |
| -------------------------- | ------ |
| Typed intent (`ActionPlan`) | ✅ |
| Deterministic compilation  | ✅ |
| Real user capability grants | ✅ |
| Audit receipts             | ✅ |
| Generated native UI        | 🟡 small component set |
| Zero procedures            | 🟡 engine exists, production routing pending |
| Zero production routing    | ❌ `WorkflowCall` still delegates to `WorkflowEngine` |
| Shadow planning            | 🟡 evaluator exists, not the live selector |
| Procedure mining           | 🟡 miner + cache seam, no persistence feed |
| Micro-agent event mesh     | 🟡 bus exists, app-side sink pending |
| Effect broker              | 🟡 grants exist, typed effects pending |
| Append-only traces         | ✅ built (harvested from PR #2; agent_run_events append-only trace, verified + gated) |
| Conversation revision guard| ✅ built (harvested from PR #2; conversation revision guard, verified + gated) |
| Shadow workspace           | ❌ designed |
| Terminal sessions          | ❌ designed |
| Browser sessions           | ✅ built (substrate: commands, observations, effect gate, state machine, receipts); app-side adapter deferred |
| GenerationService          | ❌ designed |
| Compute abstraction        | ✅ built (substrate: commands, requirements, observations, effect gate, state machine, receipts); adapters and ServiceWorld deferred |
| Agent scenario lab         | ❌ designed |
