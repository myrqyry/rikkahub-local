<!-- meristem-template:v1 -->
# Learnings

Project-local reusable lessons. A learning should prevent future work or error, not merely narrate what happened.

Use entries like:

## YYYY-MM-DD — Learning

**Signal / failure:**  
**Mechanism:**  
**Scope:**  
**Evidence:**  
**Recommended behavior:**  
**Validation status:** observed | tested | unvalidated

## 2026-10-07 — LiteRT-LM manifest identity is not the same as a model filename

**Signal / failure:** `LiteRtCatalogEntry.resolveUrl()` builds `.../resolve/main/<file>` and the installed-model UI passes that URL directly to `ModelInstall.download`. Resume appends any HTTP 206 body to a `<target>.partial` keyed only by the local filename. The current downloader checks binary magic before promotion but does not verify a publisher SHA-256 or a requested `Content-Range` start. Its comment assumes HF `resolve` URLs are immutable per commit, even though these callers use mutable `main`.

**Mechanism:** Two different revisions can use the same filename. A partial download from one revision could be appended with bytes from another and pass a first-bytes magic check; the manifest/backend compatibility could also change without a local catalog/config update. This corruption scenario is **inferred from source**, not reproduced on-device.

**Scope:** RikkaHub's existing `LiteRtCatalog` → settings picker / `SettingLocalLlmViewModel` → `ModelInstall.download` path. Its separate `litert/install/ModelInstallSession` already has hash-based promotion semantics for a different capability-scoped installer, and should be examined before creating another implementation.

**Evidence:** Source inspected on clean `aster/device-execution-profile@6695b62b` (2026-10-07): `LiteRtCatalog.kt`, `ModelInstall.kt`, `SettingLocalLlmViewModel.kt`, and `ModelInstallSessionTest.kt`. External reference: `john-rocky/hf-to-litertlm@4344637e6c8a1116253d8f6b46fef621e8e12cbb`, `manifest/SCHEMA.md`. Live LFM2.5-1.2B manifest from HF repo revision `a986969f90e60e694169b22e38b60a86e65c0448` lists `_int4` (CPU only, 736015744 bytes) separately from `_int4_gpu` (CPU/GPU, 736220768 bytes), with distinct SHA-256 values. Full provenance and hashes: `~/loam/skills/knowledge/litertlm-deployment-manifest-cutting.md`.

**Recommended behavior:** Treat manifests as untrusted *candidate metadata* until the file identity is pinned to the source revision and verified. Route backend selection through the existing measured `LiteRtExecutionPlanner`, never replace it or the current LiteRT-LM SDK just to read a manifest. For any curated download, bind partial state to immutable repo/revision/file identity, validate size/hash before promotion, check `Content-Range` for resumed transfers, and add a negative corruption/hash test. A shipped-model runtime/device test is a separate gate.

**Validation status:** observed (code and live external manifest); corruption hypothesis unvalidated; no connected ADB device; no runtime changes made.
