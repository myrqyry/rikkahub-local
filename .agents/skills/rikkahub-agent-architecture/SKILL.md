---
name: rikkahub-agent-architecture
description: Use when changing RikkaHub agent orchestration, ActionPlan flow, capability/effect authorization, executors, receipts, workflows, micro-agents, generated UI, or other architecture-bearing agent code.
---

# RikkaHub Agent Architecture

Start with `docs/references/architecture.md`. It is the canonical architecture authority.

The invariant is:

> Models propose. Deterministic layers compile, simulate and verify. The capability broker authorizes. Executors act. Receipts prove what happened.

Never let a planner, verifier, specialist, System-One judge, workflow, skill, or model become an authority boundary merely because it produced a confident answer.

## Before editing

1. Read the nearest module `AGENTS.md`.
2. Trace the real path from intent/router through ActionPlan, compiler, broker, executor, and receipt.
3. Check the subsystem status table; designed/pending is not production-wired.
4. Keep pure/JVM-testable seams in `local-llm` / `ai`; Android adapters belong in `app`.
5. Preserve the append-only AgentRun evidence path when behavior is consequential.

## Guardrails

- Typed intent beats prose-smuggled control messages.
- Capability grants and effect scopes are explicit.
- Communication buses do not gain authority.
- Candidate evaluation is side-effect free.
- Generated UI is typed state, not JSON hidden in text.
- New routing layers must preserve HARDLINE/approval behavior.
- Prefer one complete vertical slice over disconnected architecture scaffolding.

Verify both the pure seam and the selected production adapter before calling an architecture change complete.
