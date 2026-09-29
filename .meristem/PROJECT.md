<!-- meristem-template:v1 -->
# Project

## Purpose

RikkaHub Local is an experimental, local-first Android AI workspace. It extends the
RikkaHub multi-provider chat client with capability-aware assistants, on-device and
cloud model routing, explicit Android/device tools, skills and plugin imports,
workflows, documents, media, and remote integrations.

The distinctive premise is one coherent workspace in which models think, prompts
guide, skills instruct, tools act, and assistants compose them, without silently
crossing local/cloud or authority boundaries. The repository is under active
development and is not currently a finished consumer release.

## Invariants

- Sensitive tools are explicitly enabled and may require per-call approval; hard
  safety rules remain in force regardless of model intent.
- Models propose typed intent. Deterministic layers compile, simulate, and verify;
  the capability broker owns authority; executors act; receipts prove outcomes.
- Composition layers request capabilities but never own authority. See the canonical
  architecture reference for the full invariant and flow.
- Local/cloud boundaries and model capabilities must remain explicit; the system must
  not silently fall back to an unrelated provider or capability.
- Imported skills/plugins are staged, validated, reviewed, provenance-recorded, and
  activated atomically rather than blindly copied into place.
- Persistent app data, storage formats, application identity, and user configuration
  are preserved during development and upgrades unless a destructive operation is
  explicitly authorized.

## Architecture and boundaries

The Android application is the primary product surface, implemented in Kotlin,
Jetpack Compose, and Gradle Kotlin DSL for Android 8+. Feature/module boundaries
are documented by the nearest module `AGENTS.md` files. Major modules include
`app`, `ai`, `agent-runtime-adk`, `local-llm`, `common`, `workspace`, `web`,
`document`, `search`, `speech`, `highlight`, and `material3`; `web-ui` and
`locale-tui` are supporting interfaces/tools.

The stable agentic architecture, authority boundary, typed plans, receipts, module
seams, and subsystem status are owned by
`docs/references/architecture.md`, which is the canonical architecture statement.
The root `README.md` owns the public project overview and feature/status description.
The root `AGENTS.md` owns collaboration rules and standard workflows. Existing
`docs/superpowers/` plans/specs remain historical or task-specific design records;
they are not copied here.

## Known-good workflows

The root `AGENTS.md` owns the authoritative setup, development, testing, installed
data-preservation, and reporting commands. In particular:

- Android build: `./gradlew assembleDebug`
- Android unit tests: `./gradlew test`
- Android lint: `./gradlew lint`
- Web UI typecheck: `cd web-ui && pnpm run typecheck`
- Locale TUI tests: `cd locale-tui && uv run pytest`

Use the nearest module `AGENTS.md` for module-specific constraints before editing
that module. Preserve installed app data and use in-place APK installation for
device verification as specified by the root instructions.

## Verification

Verify the relevant user-visible behavior, not only compilation. Use focused unit,
lint, build, UI/instrumented, or device checks appropriate to the changed module;
report tests not run and why. For architecture and agent-runtime behavior, prefer
JVM-testable deterministic seams and receipt/event evidence. For storage or device
changes, preserve data and follow the backup/migration constraints in `AGENTS.md`.

## Open structural uncertainty

The repository contains several active experimental subsystems whose production
routing or app-side adapters are explicitly marked pending in
`docs/references/architecture.md` (for example Zero production routing, typed
effects, procedure persistence, and some event sinks). Treat that status document
and current source as authoritative rather than assuming a designed subsystem is
fully wired.
