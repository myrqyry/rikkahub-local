# Local model memory-fit badge

The Models page will show whether each installed local LiteRT language model
fits the device's current available-memory budget. The page will reuse the
runtime's existing `MemoryGuard` decision instead of creating a second memory
policy. The badge informs the user; the runtime admission check remains the
authority because memory can change between display and model loading.

## Scope

This change covers the first admission-layer slice for installed local LLMs.
It does not change model loading, assignment rules, cloud-model behavior, or
support for image, text-to-speech, speech-to-text, or embedding runtimes.

The badge applies only when all of the following are true:

- The descriptor represents a local `LiteRT` model.
- The model is installed and ready.
- The model has a known file size.

Cloud models and other local runtimes do not receive a memory-fit badge in this
slice.

## User experience

The inventory row will show a compact icon-plus-text badge. The text remains
visible so the result is understandable at a glance, and the icon receives an
equivalent content description for accessibility services.

The badge states are:

- **Fits now**: the current memory snapshot passes `MemoryGuard`.
- **Needs more memory**: the current snapshot fails `MemoryGuard`.
- **Checking**: no current memory snapshot is available yet.
- **Unavailable**: the model is outside this slice or its file size is not
  known.

The **Needs more memory** state includes the required and available memory in
supporting or accessibility text. The inventory switch remains usable exactly
as it is today. The badge does not disable a switch or assignment action.

## Architecture

The Models page owns a live memory snapshot while it is visible. It reads
`ActivityManager.MemoryInfo.availMem` and refreshes the snapshot every five
seconds. The initial snapshot is empty and produces **Checking**.

The page evaluates each visible descriptor through the pure
`MemoryGuard.decide(modelFileBytes, availMemBytes)` function. This keeps the UI
decision identical to the existing load-time policy, including its runtime
headroom calculation.

Registered local descriptors already expose their file size through metadata.
Unregistered installed inventory descriptors must expose the same
`sizeBytes` metadata when the registry creates them. The UI must not discover
file sizes independently from paths.

The admission result is derived state, not persisted model data. It must be
recomputed when the memory snapshot, model list, lifecycle, or model metadata
changes.

## Data flow

The runtime and UI use this flow:

1. `SettingsModelRegistry` creates local model descriptors and records
   `sizeBytes` for installed files.
2. `ModelsPage` obtains a current available-memory snapshot while visible.
3. The page maps each eligible descriptor and snapshot to a
   `MemoryGuard.Decision`.
4. `ModelInventorySection` renders the corresponding badge.
5. The existing runtime admission check runs again before loading and can
   refuse if the device's memory changed after the badge was rendered.

## Error handling and edge cases

Missing, malformed, non-positive, or otherwise unusable `sizeBytes` metadata
produces **Unavailable** rather than an optimistic fit result. A model that is
downloading, verifying, errored, or not installed remains outside the fit
calculation.

An unavailable memory snapshot produces **Checking** for eligible models. A
zero or negative available-memory value is passed through the existing policy,
which produces a non-fit decision for positive model sizes.

The five-second refresh is lifecycle-bound to the visible Models page and must
not continue after the page leaves composition. The badge is best-effort UI
information and must not be treated as a reservation of memory.

## Testing

Tests must cover the pure mapping from model descriptor plus memory state to
badge state, including these cases:

- An eligible model that fits.
- An eligible model that needs more memory, including required and available
  values.
- The initial checking state.
- Non-LiteRT, not-ready, not-installed, and missing-size models.
- Unregistered inventory descriptors receiving file-size metadata.

The existing `MemoryGuard` tests remain the source of truth for the numerical
admission boundary. Android UI tests are not required for the first slice
unless the existing Models page test infrastructure can exercise the badge
without adding a new framework.

## Future extension

The admission result should remain named and shaped so later runtimes can add
their own requirement calculators without changing the user-facing state
vocabulary. Image, TTS, and embedding support must be added only after each
runtime exposes a verified requirement estimate and an authoritative admission
check.
