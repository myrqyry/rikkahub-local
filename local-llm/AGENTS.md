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
| `src/main/.../llamacpp/` | llama.cpp GGUF chat provider and prompt renderer (Llamatik bridge) |
| `src/main/.../llamacpp/GgufTuning.kt` | opt-in native thread-count sweep, real generation measurements, and model-specific acceptance |
| `src/main/.../llamacpp/GgufTuningStore.kt` | applied/revert records keyed to model file, device, and Llamatik runtime |
| `src/main/.../decision/` | typed System-One decision contract and local-first reasoning-tier policy |
| `src/main/.../litert/compute/DeviceExecutionProfile.kt` | measured memory + accelerator capability snapshot |
| `src/main/.../litert/LiteRtExecutionPlanner.kt` | pure model/backend admission and recommendation policy |

## Deviations from Root

- No deviations — follow root conventions.

## Dependencies & Side Effects

- Model storage paths impact `workspace/` file management
- Terminal tests use deterministic test underlays and JVM `runBlocking`; app-side AgentRun wiring belongs in `app/`.


## GGUF Tuning Integrity

- The 1.10.1 Llamatik bridge has no native `bench()` or tokenizer-count API.
  Do not call emitted characters/second "tokens/second".
- Every candidate must reload its native model (thread config is model-init-time).
  Never benchmark against a cached context; do not race chat/vision/embedding sessions.
- The native gate in `GgufTuning.kt` serializes global bridge operations and
  closes chat sessions before benchmarking. Do not bypass this gate.
- Only explicitly applied records change the model's generation settings.
  An updated model file/device/runtime invalidates old results. Preserve easy Revert.
- A device without a registered GGUF chat model has no model to tune. Never claim
  native tuning works from Kotlin compilation alone; use a separately verified GGUF
  acceptance test and keep its storage/records isolated from user models.
