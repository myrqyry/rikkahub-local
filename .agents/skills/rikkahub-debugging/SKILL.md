---
name: rikkahub-debugging
description: Use for RikkaHub crashes, build/test failures, device-only bugs, routing mistakes, model/runtime failures, persistence regressions, or unexpected behavior. Investigate root cause before changing production code.
---

# RikkaHub Debugging

Do not shotgun fixes. Reconstruct the failing path first.

## Workflow

1. Reproduce the failure and capture the exact error/log/receipt.
2. Check the current diff and recent relevant changes.
3. Read the nearest module `AGENTS.md` and trace the real selected implementation.
4. Compare with a known-working neighboring path.
5. Form one falsifiable root-cause hypothesis.
6. Test the smallest change or diagnostic that can disprove it.
7. Fix the source, then add regression protection for the failure mechanism.

For multi-layer failures, inspect each boundary separately: UI -> service -> router/model -> capability gate -> executor -> persistence/output.

## Android/device safety

- Never uninstall the app or run `pm clear` during ordinary debugging.
- Preserve `applicationId`, databases, user config, and downloaded models.
- Prefer `adb install -r` for development APKs.
- Device-only evidence is environment-scoped; record device/backend when it matters.
- A green JVM test does not prove a device/runtime path.

Use logs, receipts, and real selection state to distinguish "implementation exists" from "production selected it."
