<!-- meristem-template:v1 -->
# Decisions

Record decisions only when the choice or its rationale is likely to matter later.

Use entries like:

## YYYY-MM-DD — Decision

**Decision:**  
**Why:**  
**Alternatives considered:**  
**Constraints / consequences:**  
**Evidence / references:**  
**Supersedes:** none

## 2026-09-20 — Local System One is the primary fuzzy decision layer

**Decision:**  
Use the reasoning ladder `deterministic -> local System One -> explicitly approved remote System One -> generative`. System-One work is limited to typed boolean, choice, and score judgments. Generative models remain responsible for synthesis.

**Why:**  
RikkaHub already prefers deterministic fast paths and typed agent plans. A small local decision layer fills the gap between exact code and expensive generative reasoning while keeping frequent routing/relevance/rule judgments private and cheap.

**Alternatives considered:**  
Use a full LLM for every fuzzy decision; center hosted Jev as the decision service; make the decision model an authority layer.

**Constraints / consequences:**  
Decision models never grant capabilities or execute tools. Local is primary. Remote decision providers require explicit authorization/data-boundary approval. Confidence is evidence, not authority. The typed contract/routing policy is implemented first; a production local model adapter must be evaluated before it is described as live.

**Evidence / references:**  
`local-llm/src/main/java/me/rerere/locallm/decision/SystemOneDecision.kt`; `docs/references/architecture.md`; shared Meristem skill `system-one-decisions`.

**Supersedes:** none

## 2026-10-05 — Local model selection uses measured execution profiles, not fixed device tiers

**Decision:**
Route Android local-inference selection through a measured `DeviceExecutionProfile` and pure `LiteRtExecutionPlanner`. Live available memory is the hard admission signal; catalog minimum-RAM bands are advisory ranking evidence. Persisted accelerator choices are revalidated against current capabilities before reuse, while native runtime fallback remains the final execution truth.

**Why:**
RikkaHub already had the right ingredients (live `availMem`, accelerator probes, memory admission, model metadata, runtime fallback) but they were independent decisions. A single profile/planner boundary prevents contradictory model/backend choices and gives future quantized variants one place to plug in without copying hardcoded device tiers.

**Alternatives considered:**
Copy Brownie's 6/8/12 GB tiers; select only from total RAM; trust cached accelerator labels indefinitely; implement advertised Q4/Q8 switching without verified matching artifacts.

**Constraints / consequences:**
The planner is evidence/policy, not capability authority. Runtime fallback is reported as degraded execution, not proof the preferred backend worked. LiteRT-LM backend labels must map to real SDK backends; 0.17.0 has no NNAPI backend, so NNAPI remains available only to compute/task adapters that truly support it. Do not claim adaptive quantization until verified Q4/Q8 artifacts exist.

**Evidence / references:**
`local-llm/.../litert/compute/DeviceExecutionProfile.kt`; `local-llm/.../litert/LiteRtExecutionPlanner.kt`; `ExecutionProfileProbe.kt`; `LiteRtRuntime.ensureLoaded`; `docs/references/architecture.md`.

**Supersedes:** none

## 2026-10-08 — LiteRT model capabilities require bundle evidence and device execution receipts

**Decision:** On a selected .litertlm chat model, inspect the official LiteRT-LM 0.18.0 `ModelInfo` when possible. A positive LLM bundle declaration can restrict actual image/audio encoder initialization and constrain the model's requested maximum tokens; an embedding-only bundle is not accepted by the chat engine. An unrecognized or uninspectable legacy bundle falls back to curated catalog settings rather than treating unknown as unsupported. An unknown model filename does *not* independently prove TOOL support for newly registered models.

**Why:** Filename/catalog estimates were used as if they were built-in features of every downloaded model, and arbitrary Hugging Face imports were labelled tool-capable. Gallery's modern `OnDeviceEmbedder` now reads `ModelInfo` to constrain execution; LiteRT-LM introduced a richer model file metadata contract in 0.18.0.

**Alternatives considered:** Trust only filenames, automatically enable every advertised modality, treat any metadata parse failure as a negative result, or populate device capability results directly from the model header. Each would conflate at least two evidence levels.

**Constraints / consequences:** Bundle-declared support is **not** hardware/backend availability, verified native inference, native tool execution or user authorization. Current installed user overrides remain intact; additive migrations never remove existing manually set capabilities. The model import/download path still needs immutable, hash-verified artifacts before parsing untrusted arbitrary files for UI registration, so bundle introspection currently happens lazily on selected engine load. Audio/vision are only forwarded after post-load execution gates. Core LiteRT 2.3.0 remains a separate, unqualified branch. Pixel validation of LiteRT-LM 0.18.0 remains outstanding while ADB is disconnected.

**Evidence / references:** `local-llm/src/main/java/me/rerere/locallm/litert/LiteRtFileCapabilities.kt`, `LiteRtRuntime.ensureLoaded`, `LiteRtModelMetadata`, `LiteRtProvider`; official `google-ai-edge/LiteRT-LM` v0.18.0 `ModelInfo.kt`; `google-ai-edge/gallery` 1.0.20 `OnDeviceEmbedder.kt`. Host tests/build: `/tmp/rikkahub-modelinfo-integrated-20261008.log` (full local-llm JVM unit test task, app and instrumented APK assemble successful). No device claim.

**Supersedes:** none

## 2026-10-13 — Port OpenCode's Meristem visual language as opt-in Android styling

**Decision:** Add a selectable `meristem` theme family in RikkaHub with two variations and the eight project-accent categories used by the OpenCode design; restyle the local/remote workspace list and OpenCode conversation/composer with compact raised surfaces, subtle accent edges, and readable outlines. Leave the user's existing theme selection untouched.

**Why:** The OpenCode `meristem-desktop` branch introduced a quieter soft-material surface hierarchy, compact project identity, and theme-based accents. These are reusable design mechanisms; CSS geometry, desktop sidebar mechanics, and simulated session state are not.

**Alternatives considered:** Globally replace Material 3 colors/shapes; embed the OpenCode desktop web UI in Android; copy its CSS verbatim.

**Constraints / consequences:** Existing theme families and chat/model/inference screens remain behaviorally unchanged. Meristem colors are Android tonal adaptations, not exact CSS token parity. Workspace connection/operation status must come from observed backend state; do not infer liveness from an attractive indicator. Theme selection uses the pre-existing Settings → Theme UI and requires disabling dynamic color as with other presets. No phone APK installation was performed during this styling pass.

**Evidence / references:** OpenCode `~/loam/opencode-upstream/packages/app/src/index.css`, `packages/app/src/pages/layout/meristem-sidebar.css`; RikkaHub `ui/theme/{Theme.kt,ThemeFamily.kt}`, `ui/pages/extensions/workspace/{WorkspacePage.kt,OpenCodeWorkspaceDetailPage.kt}`; focused `MeristemThemeTest`. Host Kotlin compilation, focused tests and debug APK assembly passed on 2026-10-13.

**Supersedes:** none

## 2026-10-13 — Two built-in visual systems, with safe legacy-theme fallback

**Decision:** Retire the five older built-in preset palettes (Sakura, Ocean, Spring, Autumn, Black) and five theme-kit families (Dracula, Catppuccin, Rosé Pine, Tokyo Night, Gruvbox). Material You remains wallpaper-derived by default on Android 12+ (custom seed optional), and Meristem is the sole static built-in theme, with two variations and eight accents. Make theme choice explicit and visible in Settings → Theme, instead of a separate Dynamic Color switch and preset carousel.

**Why:** The old selection split the same decision across pages and left a dozen stylistic directions in the main appearance UI. The compact dual-choice preview now shows actual color/surface hierarchy. Both modes use restrained, consistent Material 3 shape geometry. The Material You seed editor is folded by default, so it no longer dominates the page.

**Compatibility:** Preserve existing settings, custom/imported palette records and color sources; never delete their app data. Existing saved retired preset IDs resolve visually to Meristem without silently rewriting the stored ID; selecting Meristem commits the new ID through the normal preference update. User-authored custom palettes remain available under a collapsible section. Below Android 12, Material You uses a generated fallback rather than silently choosing a retired preset. Deleting an active custom palette selects Meristem. Changes to the old palette implementations remain recoverable from Git history.

**Evidence:** Focused theme unit tests passed; `:app:compileDebugKotlin` and `:app:assembleDebug` passed. A private, checksum-verified backup of databases, shared preferences and DataStore was created before a safe in-place Pixel 10 Pro debug update. The new screen was inspected on-device in both Material You and Meristem modes; after the test, Material You was reselected with its pre-existing custom source color intact. App database and DataStore paths were observed after installation. No user model weights or app data were removed.

**Scope:** This does not redesign every feature screen. Existing CardGroup styling and non-theme settings pages remain as they were; the shared shape tokens and new theme chooser carry the main appearance improvement. No remote push.
