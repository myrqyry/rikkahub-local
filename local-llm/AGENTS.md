# Local LLM — On-Device Model Management

## What Lives Here

Downloading, managing, and running on-device LLM models. Model lifecycle, storage, loading,
workspace boundaries, and the pure terminal execution/observation substrate.

## Key Files

| File | Purpose |
|------|---------|
| `src/` | Model download, storage, loading |
| `build.gradle.kts` | Module dependencies |
| `src/main/.../litert/terminal/` | Capability-gated process sessions, output observation, and receipts |
| `src/main/.../litert/workspace/` | Workspace refs, shadow files, and command effect analysis |

## Deviations from Root

- No deviations — follow root conventions.

## Dependencies & Side Effects

- Model storage paths impact `workspace/` file management
- Terminal tests use deterministic fake underlays and JVM `runBlocking`; app-side AgentRun wiring belongs in `app/`.
