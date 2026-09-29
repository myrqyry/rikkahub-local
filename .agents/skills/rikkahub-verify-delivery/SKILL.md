---
name: rikkahub-verify-delivery
description: Use before claiming a RikkaHub feature, bug fix, migration, local-model path, workflow, tool, or UI behavior is complete. Verify the real selected runtime path and preserve installed user state.
---

# RikkaHub Verify Delivery

Completion means the requested behavior is reachable through RikkaHub's real runtime, not merely that files compile or a test constructs the implementation directly.

## Outcome contract

For each requested outcome identify:

- user/caller entry point;
- selected implementation path;
- externally observable result;
- strongest practical verification;
- state/data that must survive.

Search for mocks, placeholders, unregistered adapters, silent fallbacks, feature flags, and UI-only state updates that could create false completeness.

## Verification ladder

1. static: compile/typecheck/lint/schema;
2. focused tests for changed logic and failure mechanism;
3. integration through real registration/DI/storage/model/tool boundaries;
4. user-visible/device behavior;
5. receipt/log/state evidence showing the intended backend/path actually executed.

For model/runtime work, a fallback result does not prove the preferred engine ran. For architecture work, a seam with no production selection is scaffolding.

## Preservation

Never uninstall, clear app data, reset databases, change application identity, or replace persistent storage without explicit authorization. Use in-place installs and verified backups for migration/storage work.

Report completed work, remaining work, checks run, checks not run and why, device verification, and known risks. Do not promote lower-level evidence into a stronger completion claim.
