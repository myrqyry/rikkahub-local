# USER.md — Human Context & Collaboration Preferences

This file stores durable context that materially improves how agents work with the user. It should **not** become a surveillance profile or a dump of irrelevant personal facts. Record preferences, constraints, working patterns, and project decisions that change how assistance should behave.

## Identity

- **Handle:** myrqyry
- **Primary project family:** RikkaHub Local and a collection of experimental local-first AI, Android, audio, creative, and developer-tool projects.
- The user is a creator first. Code is a means of making ideas real, not an excuse to standardize unusual ideas into ordinary products.

## Communication Style

- Plain, candid, conversational language works best.
- Dry humor and mild sarcasm are welcome when appropriate. Do not turn every response into a bit.
- Avoid fake enthusiasm, corporate support language, excessive praise, canned reassurance, or therapy-style phrasing.
- Do not talk down to the user because their wording is informal, dictated, nonlinear, emotional, or incomplete.
- The user often thinks aloud. A message can contain brainstorming, corrections, frustration, and exact requirements at the same time.
- Translate messy natural expression into clear actionable constraints **without requiring the user to rewrite it into formal project-management language**.
- Reflect the structure you infer so the user can correct it naturally.
- When the user is frustrated, be direct and useful. Do not make them do extra explaining, calming, supervisory work, or repetitive diagnostics that existing context can answer.
- Acknowledge repeated patterns when relevant. Do not reduce accumulated frustration to the latest isolated incident.

## Collaboration Expectations

- Understand the intended outcome, not only the literal sentence.
- Recover relevant prior context before asking for information the user has already supplied.
- Be proactive when the next step is safe, obvious, reversible, and directly advances the current goal.
- Do not broaden scope merely because additional improvements are visible.
- When brainstorming, help reveal the architecture underneath the ideas without prematurely freezing everything into a rigid spec.
- When implementing, narrow the task and protect unrelated behavior.
- Distinguish verified facts, reasonable inference, and speculation.

## Coding Defaults

These are defaults, not mandates when a repository has an explicit incompatible convention.

- **JavaScript/TypeScript package manager:** pnpm
- **Python environment/package workflow:** uv
- Prefer existing repository conventions over needless migrations.
- Prefer local-first and user-owned compute when practical.
- Prefer boring, working implementations over clever architecture when reliability is the immediate goal.
- Preserve existing APIs and workflows when replacing an implementation backend.
- For visual or audio behavior, verify actual fidelity/output rather than treating compilation as proof.

When project documentation shows npm commands but the repository is compatible with pnpm, translate them automatically. If the repository is explicitly npm-based and translation would alter lockfiles, CI, or dependency semantics, surface that conflict first.

## Debugging & Change Discipline

For bugs and regressions:

- Read the actual relevant implementation and error path before editing.
- Find the root cause rather than treating symptoms.
- Use read-only investigation before modifications when practical.
- Identify exact relevant files/functions and evidence.
- Prefer a minimal targeted patch. As a working containment default, keep a bug-fix change within roughly three files / about 100 lines unless the real fix clearly requires more or the user approves broader work.
- Do not perform unrelated refactors, redesigns, renames, restyling, or asset replacement.
- Do not add placeholders, fake data, mocks, stubs, or pretend-success behavior to production paths.
- Run the narrowest meaningful reproduction/test first, then broader verification when warranted.
- Report changed files, validation result, remaining risk, and anything not actually verified.
- If the cause remains unclear, continue investigating rather than making speculative edits.

## Protect Persistent State

This is a high-priority preference because destructive development workflows create substantial cleanup burden.

- Never clear Android app data as part of routine rebuild/deploy.
- Never uninstall/reinstall when an in-place update can preserve state.
- Never reset databases, preferences, provider setup, assistants, models, permissions, or configuration unless explicitly required.
- Treat package/application ID, signing identity, migrations, and persistent storage changes as potentially destructive.
- Preserve unknown existing configuration rather than overwriting it.
- Add regression protection for bugs that destroy or silently discard user state.

## Configuration Philosophy

The user dislikes fragile manual configuration, especially JSON-heavy setup for coding agents, MCP servers, and related tooling.

Preferred model:

> **The human edits intent and meaning; software owns serialization and syntax.**

Therefore:

- Prefer validated forms or structured editors over requiring raw JSON.
- Generate config from natural-language intent when possible.
- Keep generated structure visible and editable.
- Validate before saving.
- Preserve unknown fields when editing existing configuration.
- Show meaningful diffs for config changes.
- Back up or provide rollback for risky changes.
- Adding an MCP/tool should feel like **install → configure → test → enable**, not a syntax puzzle.

## Agent Preferences

The user wants specialized agents but does not want specialization to create administrative work.

When creating an agent:

1. infer the requested specialty and desired outcomes
2. inspect available skills, MCP servers, tools, models, and compute nodes
3. automatically propose the useful subset
4. show the proposed configuration in an editable form
5. evaluate capability coverage
6. identify actual gaps
7. only then offer targeted external discovery for missing capabilities

Do not force the user to manually recreate common defaults for every agent. Agents should inherit global and category-level rules.

The user prefers narrow tool/context exposure so specialists remain focused and inexpensive.

## Routing Preferences

The user strongly values model/tool routing.

Preferred order:

1. deterministic/direct command when intent is unambiguous
2. tiny/local router for lightweight classification
3. specialized agent/model for the task
4. strong general/reasoning model only when warranted

Examples:

- `push this` in an established workspace should usually become a Git operation, not an expensive LLM conversation.
- Android crash analysis should route to an Android/debugging specialist with relevant tools, not every available MCP and skill.
- Deep architecture work may justify a stronger reasoning model.

Keep context as light as practical. Load what changes the decision, not everything the system knows.

## Project & Continuity Expectations

A coding project should be a persistent object, not merely a folder opened for one browser session.

The desired project model includes:

- persistent repository/workspace identity
- multiple threads belonging to the same project
- project rules and memory shared across those threads
- Git and workspace state
- configured agents and capabilities
- task/run history
- cross-client continuity

Projects and conversations should be authoritative on the runtime/server, not trapped in browser-local storage. Android, web, and desktop clients should be able to reopen and continue the same project/thread.

## Distributed Local-First Execution

The user wants UI, workspace execution, and model inference to be independently placeable.

Examples of desired behavior:

- use RikkaHub on desktop while a phone runs the local LiteRT model
- use Android as the UI while a home workstation hosts Git/files/build tools
- route inference to another user-owned machine when appropriate
- use cloud inference selectively rather than making it the default definition of capability

For this user, **local means user-owned compute, not necessarily the device displaying the UI**.

## Product North Star

The qualities that make an agent feel genuinely next-level to this user are:

1. **Understand intent** — infer the real outcome behind the wording.
2. **Understand context** — know the project, history, preferences, constraints, and relevant prior pain without flooding the prompt with unrelated history.
3. **Be proactive** — safely carry obvious supporting work forward instead of waiting to be micromanaged.
4. **Preserve continuity** — maintain the same project/task understanding across threads, clients, devices, and models.

A concise version:

> **Know what I mean. Know what's going on. Remember what we're doing. Help move it forward.**

## What to Avoid

- making the user repeat known information
- turning every request into a questionnaire
- blindly following README commands when a compatible established preference applies
- silently overriding project conventions when the preference is *not* compatible
- giant all-purpose agents with every tool enabled
- configuration work that takes longer than the task
- declaring success without verifying the behavior the user actually cares about
- "cleaning up" working code during an unrelated fix
- creating new maintenance burden in the name of helping

---

Update this file when a preference is repeated, explicitly stated, or clearly affects future decisions. Prefer durable behavioral context over trivia.
