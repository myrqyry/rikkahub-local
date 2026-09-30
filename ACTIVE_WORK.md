<!-- meristem-template:v1 -->
# Active Work

## Outcome

Finish verification of the landed device-MCP and constrained-routing substrate,
then harden the typed System-One boundary before a concrete decision backend is
allowed to influence routing.

## Current state

**Base:** `master` is at `b54875eb` after `11d3b1fe feat: add device MCP and
routing substrate`. The repository state observed before this branch was clean and
synchronized with `origin/master`.

**This branch:** `aster/system-one-validation-2026-09-28` adds request/result
validation for the System-One contract and focused regression coverage.

**Submodules:** both repository pins are valid and reachable:

- `material3/material-color-utilities` -> `6fd88eb3e95ba1d457842e2a2bf847d06b3a018a`
- `third_party/stable-diffusion.cpp` -> `d2ccecd0f17b2e9dc062e158246d33962fb0dd9d`

The local checkout used for inspection had those submodules uninitialized, which
explained the local Material 3 unresolved references and missing Stable Diffusion
CMake source. The GitHub Android workflow performs recursive submodule checkout.

## Locked decisions

- Preserve installed app data, persistent storage, application identity, and
  existing model/provider lifecycle behavior.
- Device MCP stays a narrow capability export layered on top of the active
  assistant's already-enabled local tools.
- Remote device export remains authenticated, explicitly opted in, and limited by
  the hard remote-safe allowlist until a dedicated remote grant/approval flow exists.
- System-One engines remain side-effect free and never own capability authority.
- Malformed decision output must fail at the typed request/result boundary before
  callers can route on it.

## Verification evidence

Observed locally before this branch:

- `:local-llm:testDebugUnitTest --tests me.rerere.locallm.decision.SystemOneDecisionTest`
  completed successfully.
- A broader local debug build could not be treated as source evidence because the
  two git submodules were not initialized.
- No adb device or local AVD was available for UI-tree, screenshot, or logcat QA.

GitHub CI on this branch is the clean verification path because it performs
recursive submodule checkout and runs:

1. full unit tests
2. lint
3. debug APK assembly
4. targeted API-35 migration instrumentation on a disposable emulator

## Current objective

1. Validate System-One results against the exact typed request: unique question IDs,
   exact answer IDs, matching answer types, declared choice membership, and declared
   score ranges.
2. Let GitHub CI verify the clean recursive-submodule checkout.
3. Keep physical-device/UI-flow verification explicitly unclaimed until an adb target
   is available.

## Remaining runtime verification

The MCP UI/runtime path still needs an adb target for end-to-end validation of:

`Web Server -> JWT auth -> Expose device tools -> /mcp -> rikka.device.*`

Do not describe that flow as device-verified until UI-tree/logcat/runtime evidence
has been captured.
