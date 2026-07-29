# Workspace — File & Workspace Management

## What Lives Here

File manager, workspace organization, and storage management for the on-device agent.

## Key Files

| File | Purpose |
|------|---------|
| `src/` | File ops, workspace state, storage |
| `build.gradle.kts` | Module dependencies |

## Deviations from Root

- No deviations — follow root conventions.

## Dependencies & Side Effects

- Workspace file paths are consumed by `ai/` tool execution and `local-llm/` model storage