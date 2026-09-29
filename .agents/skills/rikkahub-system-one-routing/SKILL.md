---
name: rikkahub-system-one-routing
description: Use when adding or changing RikkaHub routing, classification, relevance, rule checks, tool/skill selection, confidence scoring, or other constrained fuzzy decisions. Preserve the reasoning ladder: deterministic first, local System One second, approved remote decision fallback third, full generative reasoning only when synthesis is required.
---

# RikkaHub System-One Routing

RikkaHub's preferred reasoning ladder is:

1. deterministic code for exact/mechanical decisions;
2. local System-One decision inference for fuzzy boolean / choice / score questions;
3. explicitly-approved remote System-One provider only as a fallback/benchmark;
4. full generative model for synthesis or when no reliable decision backend is available.

Do not turn a narrow classifier into a tiny chatbot.

## Current code

Read these before changing routing:

- `local-llm/.../decision/SystemOneDecision.kt` — typed decision contract and tier-selection policy.
- `app/.../skills/FastPathRouter.kt` — deterministic pre-LLM intent matches.
- `app/.../skills/LightweightSkillSelector.kt` — deterministic/BM25 skill ranking.
- `local-llm/.../litert/ActionPlanCompiler.kt` — deterministic plan validation/repair.
- `local-llm/.../litert/ShadowCandidateEvaluator.kt` — deterministic candidate scoring.
- `docs/references/architecture.md` — canonical authority boundary.

The local System-One model adapter is not yet production-wired. Do not claim otherwise.

## Rules

- Prefer typed questions with bounded outputs.
- Keep input state narrow; do not dump the whole conversation into a decision call.
- Record the provider/backend for consequential decisions.
- Calibrate thresholds against real examples before auto-acting on fuzzy judgments.
- A model decision never grants tool authority or bypasses HARDLINE/capability checks.
- Local/cloud boundaries must be explicit; remote fallback requires authorization.
- If the task needs explanation, plan generation, transformation, or open-ended reasoning, route to the generative layer instead.

When integrating the local backend, add focused JVM tests for routing policy plus real-backend evaluation cases. Preserve a generative fallback until the local decision model is proven reliable for that question family.
