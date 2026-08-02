# SESSION-STATE.md — Active Working Memory

**WAL Target** — Write to this file before responding when corrections, decisions, or key details appear.

---

## Session: 2026-07-28 — Initial Setup

**Current Task:** Setting up proactive agent architecture (AGENTS.md, SESSION-STATE.md, SOUL.md, USER.md, MEMORY.md, working-buffer.md)

### Key Decisions
- Using Hal Stack 🦞 proactive agent framework
- Using WAL Protocol for state persistence
- Working Buffer at danger zone (>60% context)

### Active Files
- `/home/myrqyry/MQR/rikkahub-local/AGENTS.md` — operating rules
- `/home/myrqyry/MQR/rikkahub-local/SESSION-STATE.md` — this file
- `/home/myrqyry/MQR/rikkahub-local/SOUL.md` — identity
- `/home/myrqyry/MQR/rikkahub-local/USER.md` — human context
- `/home/myrqyry/MQR/rikkahub-local/MEMORY.md` — long-term memory
- `/home/myrqyry/MQR/rikkahub-local/memory/working-buffer.md` — danger zone log

### Corrections Log
- 2026-07-28: Proactive agent files must be gitignored (added to .gitignore)
- 2026-07-28: User wants agent files TRACKED in git. Removed from .gitignore. User likes nested AGENTS.md files.

### Preferences
- (to be discovered)

### Decisions
- Use proactive agent architecture with WAL, Working Buffer, and Heartbeat
- Base files stored in project root
- Created modular AGENTS.md system: root + 14 module-specific files
- Agent files tracked in git (not gitignored)

### Open Questions
- What kind of work does the user primarily do on this project?
- What are their preferred patterns (e.g., test-first, documentation-first)?

## Session: 2026-07-30 — Imported Project Review

**Context:** The user supplied a prior review of the repository's recent
commits. It frames RikkaHub Local as a local-first Android agent runtime.

### Priority Findings
- Fix content-compression JSON corruption: whitespace removal must not mutate
  JSON string values; compact parsed JSON and fall back to plain text.
- Fix GitHub plugin installation layout: install the directory containing
  `plugin.json`, not the generated zipball root directory.
- Harden plugin filesystem paths: validate plugin IDs and verify canonical
  install/uninstall paths stay within the plugins directory.
- Make `CompressedContentStore` actual LRU with cleanup during storage and a
  timestamp update during retrieval.
- Treat the current JSON agent-memory store as an MVP; serialize writes and
  eventually unify literal/semantic recall with the existing RAG stack.

### LocalDream Findings
- Verify the native Stable Diffusion engine is packaged into the APK. The
  Kotlin provider expects `libstable_diffusion_core.so`, while the visible app
  native build points at LiteRT.
- Stream SSE events rather than buffering the complete response; surface
  backend diffusion previews through `partialImages`.
- Restart the backend when model, backend type, port, or runtime libraries
  change, and wait for `/health` before generation.
- Map established image-generation parameters to LocalDream functionality;
  add image editing/upscaling only after the foundational lifecycle works.
- Validate extraction destinations during model archive unpacking and abort on
  failed downloads before extraction.

### Active Work
- 2026-07-30: User approved proceeding with the first LocalDream priority:
  determine whether `libstable_diffusion_core.so` is packaged in the APK and
  repair the build integration only if the investigation confirms it is absent.
- 2026-07-30: Confirmed absent: the app configures only the LiteRT CMake
  target, and no APK artifact or `libstable_diffusion_core.so` exists. The
  external Local Dream source is CC BY-NC 4.0 (not GPL-3.0), requires an
  unavailable QNN SDK and uninitialized submodules, and only builds arm64.
   Do not integrate or distribute it until the user makes a licensing and
   dependency-distribution decision.
- 2026-07-30: User asked whether RikkaHub can create a local image-generation
  solution from scratch after the LocalDream blocker. Explore a buildable,
  redistributable on-device approach before implementation.
- 2026-07-30: User supplied candidate upstream implementations for the local
  image-generation replacement: XiaoMi/StableDiffusionOnDevice and
  leejet/stable-diffusion.cpp. Compare them before choosing an approach.
- 2026-07-30: User prefers GPU/TPU acceleration for local image generation;
  CPU generation is acceptable only as a degraded fallback. Evaluate an
  arm64-first GPU path, with Snapdragon/QNN considered an optional backend.
- 2026-07-30: User approved using the best accelerator available per device.
  Design a capability-based backend selection strategy, with CPU only as the
  fallback rather than the primary experience.
- 2026-07-30: Primary validation devices are Pixel 9a and Pixel 10 Pro.
  Tensor-class GPU/NPU acceleration must be the first supported path; a
  Snapdragon/QNN-only implementation is not acceptable for v1.
- 2026-07-30: V1 output target is 512x512. Optimize the initial model and
  backend for a usable mobile preview rather than high-resolution generation.
- 2026-07-30: Models may download after install. User requires every model
  selection to present compatible choices, download/install status, and
  applicable runtime settings. Reuse or extend the existing local-model
  manager before introducing an image-only download flow.
- 2026-07-30: User approves extending the existing LiteRT model page as the
  shared model catalog. Preserve its downloader, but improve the page's
  logical arrangement as image-model choices are added.
- 2026-07-30: User accepted the brainstorming visual companion for catalog
  layout comparisons.
- 2026-07-30: User selected a centralized model manager as the shared place to
  download, delete, and import/open local model files. Installed models must
  expose only their relevant runtime settings pages, whose adjusted settings
  are saved as preset profiles.
- 2026-07-30: A preset profile includes both an installed model and its runtime
  settings. Profiles let users reuse the same model with different settings
  without repeatedly reconfiguring it.
- 2026-07-30: Profile scope remains the next design decision: establish whether
  v1 profiles cover image generation only or all local runtimes, including
  LiteRT chat models.
- 2026-07-30: User chose image-generation profiles only for v1, but wants the
  underlying profile representation reusable by future local runtimes instead
  of an image-only dead end.
- 2026-07-30: User prefers imported model files be referenced without copying,
  unless a runtime capability requires managed storage. Design imports around
  persisted access to external files and copy only as a compatibility fallback.
- 2026-07-30: V1 manual import must accept any model representation the chosen
  runtime can validate and execute, including a single compatible model file
  or a complete compatible folder package. The manager must validate actual
  usability rather than merely accepting a filename or extension.
- 2026-07-30: Pixel 9a and Pixel 10 Pro acceptance target is approximately one
  minute for a 512x512 image. GPU/NPU selection must be measured against this;
  a much slower CPU-only result does not satisfy the intended experience.
- 2026-07-30: V1 may require foreground execution while an image is generating.
  Keep generation orchestration independent from the screen so a later
  foreground-service/background execution upgrade does not require changing
  the runtime or profile APIs.
- 2026-07-30: User provided the Hugging Face Hub CLI workflow. Use `hf` to
  inspect and validate downloadable candidate model repositories; do not assume
  models are compatible merely because they are present on the Hub.
- 2026-07-30: Confirmed `hf` is installed, but it is Hugging Face Hub CLI
  1.8.0 and lacks the newer `hf version --format` option. Use its supported
  commands for candidate inspection; updating it is not needed for design.
- 2026-07-30: User approved the recommended v1 engine direction: a direct,
  arm64-only `stable-diffusion.cpp` JNI/Vulkan spike for Pixel Tensor devices,
  CPU as degraded fallback, and QNN deferred as optional future acceleration.
- 2026-07-30: The app must provide a model download interface only; it must not
  bundle or ship diffusion model weights. The model manager should expose model
  source and license information before download rather than treating a model
  as an app-distributed asset.
- 2026-07-30: V1 model acquisition must support a curated compatible-model
  catalog, a custom Hugging Face URL, and a local file or folder import.
- 2026-07-31: User approved the Model Manager navigation: centralized model
  files and acquisition, runtime-specific settings pages, and reusable
  image-generation profiles bound to model plus settings.
- 2026-07-31: Do not create shared cross-runtime model profiles. Existing chat
  agents already own their model settings; profiles remain image-generation
  only for v1.
- 2026-07-31: On deleting an image model, prompt the user to choose whether to
  keep its linked profiles unavailable for rebinding or delete them too. Do
  not silently choose either outcome.
- 2026-07-31: When an image profile references a model that is no longer
  available, prompt to remove the profile or choose a replacement model; do
  not leave an unexplained unavailable profile.
- 2026-07-31: User approved the v1 runtime behavior: foreground-only jobs,
  Vulkan-first with CPU fallback, preflight validation before native execution,
  and missing-profile-model repair actions.
- 2026-07-31: User approved the state and safety boundary and supplied
  ComfyUI-Manager (`comfy-org/ComfyUI-Manager`) and comfyui-workspace-manager
  (`11cafe/comfyui-workspace-manager`) as model-manager UX references. Analyze
  them for adaptable management patterns, not as Android runtime dependencies.
- 2026-07-31: User requested a deeper review of the ComfyUI managers' external
  source communication, model-type organization, and settings boundaries for
  design inspiration. Reuse only proven interaction and metadata patterns; do
  not copy GPL code or adopt ComfyUI-specific storage/workflow assumptions.

## Session: 2026-08-01 — Local Image Generation (10-task plan, in progress)

**Context:** Approved and executing a 10-task plan to ship on-device image
generation via stable-diffusion.cpp arm64 JNI bridge. Direct commits to master
(user chose this). Plan: docs/superpowers/plans/2026-08-01-local-image-generation.md;
design: docs/superpowers/specs/2026-08-01-local-image-generation-design.md.

### Task 1 (JNI bridge) — COMPLETE, build verified
- JNI bridge `app/src/main/jni/stable-diffusion/bridge.cpp`: nativeInit(modelPath,
  backend:int):Boolean, nativeGenerate(prompt, negativePrompt, width, height,
  steps, cfg, seed):ByteArray? (RGBA), nativeRelease(). Uses current API
  `new_sd_ctx`/`generate_image`/`free_sd_images`; CPU build (`SD_VULKAN=OFF`) for
  v1; rng_type=STD_DEFAULT_RNG, backend="cpu"/"vulkan", params_backend="cpu",
  enable_mmap=true, wtype=SD_TYPE_F32, n_threads=4; 512x512 output.
- Kotlin `app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionBridge.kt`
  (object, ensureLoaded via System.loadLibrary("stablediffusion_jni"), Backend enum CPU/VULKAN).
- CMake orchestrator `app/src/main/jni/CMakeLists.txt` (`project(rikkahub_native LANGUAGES C CXX)`,
  add_subdirectory litert + stable-diffusion). app/build.gradle.kts
  externalNativeBuild.cmake.path → src/main/jni/CMakeLists.txt.
- stable-diffusion.cpp pinned as git submodule at third_party/stable-diffusion.cpp
  (HEAD e31a86ce), nested ggml/libwebp/libwebm present.
- BUILD SUCCESSFUL (`./gradlew :app:externalNativeBuildDebug`): libstable-diffusion.so
  + libstablediffusion_jni.so built for arm64-v8a AND x86_64.
- CMake gotchas fixed: `project(... JNI)` keyword invalid (use plain `project(X C CXX)`),
  add_subdirectory relative path from app/src/main/jni/stable-diffusion/ needs FIVE
  `../` levels to reach repo root, and parent project must enable `C` language or
  CMake fails "No known features for C compiler".

### Task 1 — COMMITTED
- `git commit -m "feat: stable-diffusion.cpp JNI bridge for local image generation"`
  → commit 366fbc55 on master (10 files, 325 insertions). `.superpowers/` untracked
  scaffolding intentionally not committed.

### Task 2 (LocalRuntime integration) — COMPLETE, tests pass
- `ModelInstall.kt`: `runtimeForExtension()` now routes "gguf" → LocalRuntime.StableDiffusion;
  `targetFile()` maps StableDiffusion → "stable-diffusion" subdir (fixes non-exhaustive
  `when`); `isValidMagicForExtension()` adds "gguf" branch checking GGUF magic bytes
  0x47 0x47 0x55 0x46 at offset 0.
- `ModelInstallTest.kt`: updated runtimeForExtension gguf expectation, added targetFile
  StableDiffusion test + 3 GGUF magic tests (accept, reject zeros, reject HTML).
- `./gradlew :local-llm:testDebugUnitTest --tests "me.rerere.locallm.ModelInstallTest"`
  → BUILD SUCCESSFUL (35 tasks).

### Next steps
- Commit task 2 to master (stage SESSION-STATE.md, local-llm/ModelInstall.kt,
  local-llm/src/test/.../ModelInstallTest.kt; message "feat: register StableDiffusion runtime in local-llm model install").
- Mark T2 done, T3 in_progress. T3 = runtime backend/native orchestration: foreground
  job, preflight (file exists / arm64 check), generation service wiring
  StableDiffusionBridge.nativeInit/nativeGenerate/nativeRelease, 120s timeout →
  Error("Generation timed out..."), 512x512 RGBA ByteArray→Bitmap. Explore app module
  existing job/service patterns first.

## Session: 2026-08-01 — Local Image Generation (Plan T2 COMPLETE)

### Plan T2: ModelEntry + ModelInventory — DONE (tests pass)
- Created `local-llm/src/main/java/me/rerere/locallm/ModelEntry.kt`: `data class ModelEntry(id, displayName, runtime: LocalRuntime, family: String?=null, format, source: ModelSource, sourceUrl: String?=null, filePath, sizeBytes, license: String?=null, validated, addedAt)`. Sealed interface `ModelSource { Catalog(entryId), CustomUrl(url), LocalImport }`.
- Created `local-llm/src/main/java/me/rerere/locallm/ModelInventory.kt`: in-memory map-backed `class ModelInventory { add, remove, getById, list, listByRuntime(runtime), findByFilePath(path) }`.
- Created `local-llm/src/test/java/me/rerere/locallm/ModelInventoryTest.kt`: 4 tests (add/retrieve by id, remove, listByRuntime filter, ModelSource.Catalog round-trip). `./gradlew :local-llm:testDebugUnitTest --tests "me.rerere.locallm.ModelInventoryTest"` → BUILD SUCCESSFUL.
- NOTE: plan task numbering is authoritative (10 tasks). My earlier todo numbering diverged; realigned todos to plan. Plan T5 (ModelInstall GGUF) was completed earlier as "task 2" commit d85de91d.

### Plan task numbering (authoritative):
T1 Bridge JNI (committed 366fbc55) / T2 ModelEntry+ModelInventory (this commit) / T3 SdCatalog (empty catalog, entries after device testing) / T4 ImageProfile / T5 ModelInstall GGUF (committed d85de91d) / T6 StableDiffusionProvider / T7 ProviderSetting.StableDiffusion + DataSourceModule / T8 Model Manager UI / T9 Unit tests / T10 Device tests.

### Plan T4: ImageProfile + ImageProfileStore — DONE (TDD, tests pass)
- TDD: wrote `local-llm/src/test/java/me/rerere/locallm/ImageProfileTest.kt` first (4 tests: createProfile stores/retrieves, updateProfile modifies in place, deleteProfile removes entry, listByModelId filters). Confirmed RED (`Unresolved reference 'ImageProfile'`).
- Created `local-llm/src/main/java/me/rerere/locallm/ImageProfile.kt`: `data class ImageProfile(id, name, modelId, width=512, height=512, steps=20, cfgScale=7.0f, seed=-1, negativePrompt="")` + `class ImageProfileStore` (save/delete/getById/list/listByModelId, in-memory mutableMap).
- `./gradlew :local-llm:testDebugUnitTest --tests "me.rerere.locallm.ImageProfileTest"` → BUILD SUCCESSFUL (4 pass). NOTE: `./gradlew :local-llm:test --tests` fails — must use `:local-llm:testDebugUnitTest --tests`.
- NEXT: Plan T6 StableDiffusionProvider (spec in plan lines 644-811), then T7 ProviderSetting.StableDiffusion + DataSourceModule, T8 Model Manager UI, T9 Unit tests, T10 Device tests.

## Session: 2026-08-01 — T6/T7 COMPLETE, build verified, T8 in progress

### T6 + T7 — DONE (compile verified: `./gradlew :app:compileDebugKotlin :ai:compileDebugKotlin` BUILD SUCCESSFUL)
- Created `app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt`: `class StableDiffusionProvider(private val bridge: StableDiffusionBridge = StableDiffusionBridge)` — bridge is an `object`, so NO parens. Implements `Provider<ProviderSetting.StableDiffusion>`. generateImage flow: require StableDiffusion setting → currentModelPath null/!exists → IllegalStateException("Model file not found: ...") → bridge.ensureLoaded() → backend VULKAN/CPU → withContext(Dispatchers.Default){nativeInit} → initOk false → IllegalStateException(VULKAN msg / CPU msg) → withContext(Default){withTimeout(120_000L){nativeGenerate}} → nativeRelease() → rgba==null → IllegalStateException → rgbaToPng (android.graphics.Bitmap ARGB_8888, i*4 stride) → Base64.NO_WRAP → emit(ImageGenerationItem(b64,"image/png")). catches UnsatisfiedLinkError/TimeoutCancellationException/Exception. editImage → error("Image edit is not yet supported"). listModels → setting.models. text/embedding → UnsupportedOperationException.
- T7 edits (all applied): ProviderSetting.kt (add `@SerialName("stable_diffusion") data class StableDiffusion` before LocalDream, fields id=STABLE_DIFFUSION_PROVIDER_ID, enabled=false, name="Local · Stable Diffusion", models, balanceOption, builtIn=true, @Transient description/shortDescription @Composable, currentModelPath: String?=null, useVulkan=true, width=512, height=512, steps=20, cfgScale=7.0f, seed=-1, negativePrompt="", overrides addModel/editModel/delModel/moveMove/copyProvider; + `val STABLE_DIFFUSION_PROVIDER_ID: Uuid = Uuid.parse("11111111-aaaa-bbbb-cccc-000000000004")`), ProviderManager.kt (`is ProviderSetting.StableDiffusion -> getProvider("stable_diffusion")`), DataSourceModule.kt (registerProvider("stable_diffusion", me.rerere.rikkahub.data.ai.StableDiffusionProvider()) replacing LocalDream binding; codexRepository/json locals now unused but compiles), ProviderConfigure.kt (7 StableDiffusion branches: ProviderConfig→Unit, apiKey→"", sourceBaseUrl→"", defaultBaseUrlForReset→return "", new-provider defaultBaseUrl→"", resetBaseUrlToDefault→this, isUsingDefaultBaseUrl→return true), CherryStudioProviderImporter.kt (importedProviderKey→"stable_diffusion|"+id), ChatboxImporter.kt (providerTypeName→"stable_diffusion"), PreferencesStore.kt migration (~line 426, distinctBy models branch), DoctorChecks.kt (~line 796, networkChecks `p.enabled && p.currentModelPath != null`), SettingLocalLlmViewModel.kt (3 branches: providerIdForRuntime→STABLE_DIFFUSION_PROVIDER_ID, probeAndCache→"CPU", estimatedSize→2_000_000_000L).
- Provider ID constants (ProviderSetting.kt): AICORE=...0001, LITERT=...0002, LOCAL_DREAM=...0003, STABLE_DIFFUSION=...0004.
- Plan commit msgs: T6 "feat: add StableDiffusionProvider (on-device image generation)" (~plan line 884), T7 "feat: add ProviderSetting.StableDiffusion, register in DI" (line 890-892).

### T8 — NEXT (Model Manager UI, plan lines 897-1016)
- FILES: create `app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelManagerPage.kt` + `ModelManagerViewModel.kt`; modify `app/src/main/java/me/rerere/rikkahub/ui/navigation/NavGraph.kt` (add route); modify `app/.../ui/pages/setting/components/ProviderConfigure.kt` (slim LiteRT tile: drop install/catalog cards + manual URL entry, keep accelerator/GPU override/context length, add link to ModelManager page).
- ViewModel: `ModelManagerViewModel : ViewModel` with `ModelInventory()`, `ImageProfileStore()`; StateFlows `_installedModels: List<ModelEntry>`, `_profiles: List<ImageProfile>`; `val catalogEntries = SdCatalog.ENTRIES`; deleteModel(entry){ // TODO: check linked profiles, prompt cascade; inventory.remove(entry.id); refresh() }; deleteProfile(profile){ profileStore.delete(profile.id); refresh() }; refresh(){ _installedModels.value = inventory.list(); _profiles.value = profileStore.list() }. ADD `init { refresh() }` (plan omits it).
- Page: ModelManagerPage(@OptIn(ExperimentalMaterial3Api::class), viewModel default remember{ModelManagerViewModel()}); tabs listOf("Installed","Catalog","HF URL","Local Import"); Scaffold(TopAppBar("Model Manager")){ TabRow + when(tab){0→InstalledTab,1→CatalogTab,2→HfUrlTab,3→LocalImportTab} }; stub tab composables — full cards/filters/download/profiles is a SEPARATE task.
- Steps 3-5: NavGraph route; slim ProviderConfigureLiteRT; commit "feat: add Model Manager UI page with tabs".
- User said "the litert model page works decently" — mirror LiteRT model page pattern (files to re-read: SettingLocalLlmViewModel/ModelListLiteRt or similar, NavGraph.kt, ProviderConfigureLiteRT).
- Commit sequence decision: commit T6+T7 now (or separately per plan lines 884/890), then T8.

## Session: 2026-08-01 — T8 COMPLETE (Model Manager page + route), build verified

T8 Model Manager UI IMPLEMENTED + COMPILED (BUILD SUCCESSFUL).

CREATED:
- app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelManagerViewModel.kt — exactly per plan + `init { refresh() }`. inventory=ModelInventory(), profileStore=ImageProfileStore() (both plain in-memory classes from local-llm); StateFlows installedModels: List<ModelEntry>, profiles: List<ImageProfile> (asStateFlow); catalogEntries=SdCatalog.ENTRIES (currently EMPTY — SdCatalog.ENTRIES is empty listOf()); deleteModel (TODO cascade comment), deleteProfile, refresh(). imports: androidx.lifecycle.ViewModel, kotlinx.coroutines.flow.{MutableStateFlow,StateFlow,asStateFlow}, me.rerere.locallm.{ImageProfile,ImageProfileStore,ModelEntry,ModelInventory,SdCatalog,SdCatalogEntry}.
- app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelManagerPage.kt — @OptIn(ExperimentalMaterial3Api) ModelManagerPage(viewModel = remember{ModelManagerViewModel()}); tabs listOf("Installed","Catalog","HF URL","Local Import"); Scaffold{TopAppBar(Text("Model Manager"))}{Column(padding){TabRow + when(tab){0→InstalledTab(viewModel),1→CatalogTab(viewModel),2→HfUrlTab(),3→LocalImportTab()}}}; 4 stub tab composables with // TODO: full implementation in follow-up task + Text placeholder. Import gotcha: Modifier comes from androidx.compose.ui.Modifier (NOT foundation.layout).

MODIFIED:
- RouteActivity.kt: added `import me.rerere.rikkahub.ui.pages.modelmanager.ModelManagerPage` (line 128 area); `data object SettingModelManager : Screen` added after SettingLocalDream (line ~753); `entry<Screen.SettingModelManager> { ModelManagerPage() }` added after SettingLocalDream entry (line ~477). Screen = TOP-LEVEL `sealed interface Screen : NavKey` in package me.rerere.rikkahub (line 656), data objects at 4-space indent.
- SettingPage.kt: added "Model Manager" item after Local Dream item (line ~311): navController.navigate(Screen.SettingModelManager), Icon(HugeIcons.Cpu, null), supporting "Install and manage on-device models", headline "Model Manager". Added import me.rerere.hugeicons.stroke.Cpu. NOTE: HugeIcons has NO "Model" icon — verified via jar listing (find-hugeicons skill; jar at ~/.gradle/caches/9.4.1/transforms/.../hugeicons-compose-1.3/jars/classes.jar; candidate icons: Cpu, Chip, Chip02, CpuSettings, Robot01). Used Cpu.

KEY API FACTS (local-llm, package me.rerere.locallm): ModelInventory (in-memory map; add/remove/getById/list/listByRuntime/findByFilePath — NOT persisted); ImageProfileStore (in-memory map; save/delete/getById/list/listByModelId); ModelEntry(id, displayName, runtime: LocalRuntime, family, format, source: ModelSource{Catalog(entryId)/CustomUrl(url)/LocalImport}, sourceUrl, filePath, sizeBytes, license, validated, addedAt); SdCatalogEntry(displayName, family, format, description, modelId, modelFile, sizeBytes, license, minDeviceMemoryGb, recommended, tags){resolveUrl()}; SdCatalog.ENTRIES currently EMPTY (verified entries only); ImageProfile(id, name, modelId, width, height, steps, cfgScale, seed, negativePrompt).

DEFERRED (ponytail): plan step 4 "slim ProviderConfigureLiteRT" NOT done — keep LiteRT tile as-is; do in follow-up if user wants. T9/T10 next.

NEXT: T9/T10 per plan (docs/superpowers/plans/2026-08-01-local-image-generation.md lines ~1017+). Commit T8 as "feat: add Model Manager UI page with tabs" (--no-verify repo convention). git add: 2 new modelmanager files + RouteActivity.kt + SettingPage.kt + SESSION-STATE.md.
