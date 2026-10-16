# ExpActivator experience activation — research cutting (2026-10-15)

Upstream: https://github.com/fyzhang1/ExpActivator
Inspected GitHub main tree 3ef8ce5d923de56e56dc17eada833306dd92ea63.
Paper: https://arxiv.org/abs/2609.33304

## What the source demonstrates

ExpActivator distinguishes relevant *retrieved* user history from a
historical action that *applies to the current state*.

- Step-level: match current GUI screenshot to historical states, provide
  historical action as advisory evidence to a frozen GUI policy that may
  adopt, adapt or reject the suggestion.
- Task-level: activate recurring intent only with temporal and scenario
  support, otherwise abstain. Includes negative proactive states.
- Benchmarks described: 775 execution episodes, 7,915 action steps,
  215 positive and 100 negative proactive states, four GUI backbones.
  This does not establish working Android/RikkaHub integration.
- Audit: memory is chronological first 80% per user; held-out test
  episodes must not appear in its history. The repository documents
  and tests a previously leaky history split. Direct copying of the
  retrieved action is a distinct diagnostic, not policy success.
- A separate bundled hierarchical-implicit-intent-memory skill
  generalizes *PersonalAlign*, not ExpActivator's state matching.
  Its inferred preference and routine summaries are soft evidence;
  explicit user intent takes precedence.

Source locations:
https://github.com/fyzhang1/ExpActivator/blob/main/README.md
https://github.com/fyzhang1/ExpActivator/blob/main/README-skills.md
https://github.com/fyzhang1/ExpActivator/blob/main/expactivator/build_execution_grounded_evidence.py
https://github.com/fyzhang1/ExpActivator/blob/main/expactivator/evaluate_proactive.py
https://github.com/fyzhang1/ExpActivator/blob/main/expactivator/execution_core.py
https://github.com/fyzhang1/ExpActivator/blob/main/expactivator/STATE_CONDITIONING_PROTOCOL.md
https://github.com/fyzhang1/ExpActivator/blob/main/tests/test_history_split.py

Root listing exposed no LICENSE file. Do not copy implementation
code or the bundled skill without clarifying license permission.

## RikkaHub Local's current state

- Canonical docs/references/architecture.md: models propose; compile,
  evaluate and verify; broker authorizes; executors act; receipts prove.
  Any experience-matching score stays evidence, never authority.
- ProcedureMiner.kt models successful tool-name sequences from a
  ToolExecution list of tool, args and timestamp. It does not carry
  historical screen state, task identity, result context or version.
- ProcedureMiningFeed.kt maps tool receipts to this list with *empty*
  args (receipt does not contain them) and stores mined candidates
  disabled. ZeroProcedureRepository and ZeroProcedureExecutorAdapter
  require explicit enabled state and granted capabilities to act.
- AgentMemoryManager search is a keyword/recency index without
  a GUI-state applicability gate. It is a separate memory layer.

These do not constitute an implemented ExpActivator-style path.
A scorer without real current/historical UI observations would be
disconnected scaffolding. Do not wire a fake activation switch.

## Narrow valid path to implementation

1. Capture consent-scoped successful GUI/browser action episodes
   with source id, task id, app/URL identity, version, bounded current
   semantic UI state or screenshot descriptor, action and receipt.
   Exclude secrets and unapproved private data.
2. Retrieve by same-user intent, then verify current view/affordance,
   object, task phase, action target and capability availability.
   Unknown/mismatched state => abstain. Never blindly replay stored
   coordinates against changed layouts.
3. Offer a bounded provenance-bearing historical-action hint to the
   current policy; reobserve state before any action. Never grant
   authorization via retrieval or infer user approval from recurrence.
4. For proactive suggestion require recurrence AND contextual/temporal
   support, explicit user intent supremacy, and default abstention.
   Do not automatically create cron schedules from mined patterns.
5. Evaluate in shadow mode with same-goal/different-state,
   same-screen/different-object, stale app, missing observation,
   cross-user, failed episodes, wrong time, prohibited capability,
   and no-action negatives. Chronological holdout must not leak
   target answers; compare to direct copy and no-history baselines.
6. Require measured improvement in wrong-action rate, abstention,
   suggestions, memory, latency and privacy before production wiring.

This review is a design/evidence checkpoint only: no runtime change,
device operation, model download, private memory rewrite, skill
installation, schedule or upstream merge.
