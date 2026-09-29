---
name: rikkahub-local-inference
description: Use when changing RikkaHub on-device model discovery, installation, LiteRT/Llama.cpp execution, small task models, accelerators, local model metadata, memory policy, or local/cloud routing.
---

# RikkaHub Local Inference

Local inference lives primarily under `local-llm/`. Read `local-llm/AGENTS.md` and the relevant runtime source before changing it.

## Principles

- Local/cloud boundaries must be explicit; never silently substitute a cloud provider for a promised local capability.
- Preserve model/runtime identity in receipts and UI when it affects user expectations.
- Deterministic validation and memory/accelerator gates run before expensive inference.
- Small task/decision models are preferred for bounded jobs when they are proven adequate.
- A fallback is a degraded path, not evidence that the preferred local runtime worked.
- Do not infer model compatibility from filename alone when metadata/probing can decide it.

## Verification

For runtime changes, verify as far down the ladder as practical:

1. pure JVM/unit behavior;
2. model/package validation and runtime selection;
3. actual adapter load/inference;
4. device-specific accelerator/memory behavior when relevant;
5. observable result/receipt showing which backend executed.

Record device/runtime/model version for environment-sensitive claims. Preserve installed app data and model files during testing unless a destructive test is explicitly authorized.
