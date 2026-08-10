# Qwen semantic model setup implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a shared, polished setup flow for the Qwen embedding and
reranking models, with independent status, Hugging Face downloads, folder
import, validation, and clear missing-file guidance.

**Architecture:** Keep `SearchServiceOptions` as the persisted provider
configuration. Add a focused model setup manager and view model that own file
requirements, validation, downloads, folder imports, progress, and status.
Render one shared setup card from `SettingSearchDetailPage` when either Qwen
provider is selected; the card reads both provider configurations and updates
only the selected model directory values. Reuse `ModelInstall` and the
existing `OpenDocumentTree` bundle-import pattern instead of adding a new
download library or changing LiteRT inference classes.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android Storage Access
Framework, Kotlin coroutines and flows, `ModelInstall`, Koin, JUnit.

## Global Constraints

- Use compact-phone-first layout and preserve existing search settings
  navigation.
- Keep embedding and reranking readiness independent.
- Support both direct Hugging Face downloads and selecting an existing local
  model directory.
- Validate required files before reporting `Ready`.
- Use plain-language copy; expose technical filenames only as diagnostics.
- Use semantic dynamic-color tokens, restrained Material 3 Expressive styling,
  minimum 48 dp touch targets, and reduced-motion-safe transitions.
- Do not change the serialized `SearchServiceOptions` discriminator or the
  existing Qwen inference model formats.
- Do not instantiate `CompiledModel` during validation or settings rendering.
- Do not delete a previous valid installation when a download or import fails.
- Do not add a new dependency for downloading, file picking, or state
  management.

---

### Task 1: Add the Qwen model requirements and validator

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/QwenSemanticModelManager.kt`
- Create: `app/src/test/java/me/rerere/rikkahub/ui/pages/setting/components/QwenSemanticModelManagerTest.kt`

**Interfaces:**
- Produces `QwenSemanticModelManager.ModelKind.Embedder` and
  `QwenSemanticModelManager.ModelKind.Reranker`.
- Produces `QwenSemanticModelManager.ModelStatus` with `Ready`,
  `NotInstalled`, and `Incomplete` states.
- Produces `validate(directory: File, kind: ModelKind): ModelStatus`.
- Produces `requiredFiles(kind: ModelKind): List<String>`.
- Produces `modelDirectory(context: Context, kind: ModelKind): File`.

- [ ] **Step 1: Define the model requirement data.**

  Add a manager object with exact model metadata:

  ```kotlin
  object QwenSemanticModelManager {
      enum class ModelKind { Embedder, Reranker }

      sealed interface ModelStatus {
          data object NotInstalled : ModelStatus
          data class Incomplete(val missingFiles: List<String>) : ModelStatus
          data class Ready(val directory: File) : ModelStatus
      }

      private val embedderFiles = listOf(
          "qwen3emb_gpu_fp16.tflite",
          "embeddings_fp16.bin",
          "vocab.json",
          "merges.txt",
      )

      private val rerankerFiles = listOf(
          "qwen3rerank_gpu_fp16.tflite",
          "embeddings_fp16.bin",
          "vocab.json",
          "merges.txt",
      )
  }
  ```

  Keep the model directory names aligned with current runtime wiring:
  `filesDir/models/embedder` and `filesDir/models/reranker`.

- [ ] **Step 2: Implement pure file validation.**

  `validate` must treat a blank or non-directory path as `NotInstalled`,
  report every required file that is absent, not a regular file, unreadable,
  or zero bytes as `Incomplete`, and return `Ready(directory)` only when all
  required files pass. Do not construct `QwenEmbedder`, `QwenReranker`, or any
  LiteRT object.

- [ ] **Step 3: Write validator tests first.**

  Use JUnit temporary directories and assert these cases:

  ```kotlin
  @Test fun emptyPathIsNotInstalled()
  @Test fun missingFilesAreReported()
  @Test fun zeroLengthFileIsIncomplete()
  @Test fun completeEmbedderBundleIsReady()
  @Test fun completeRerankerBundleIsReady()
  ```

  Test each required filename and verify the returned `missingFiles` list is
  deterministic and complete.

- [ ] **Step 4: Run the focused tests.**

  Run:

  ```bash
  ./gradlew :app:testDebugUnitTest --tests '*QwenSemanticModelManagerTest'
  ```

  Expected result: all validator tests pass.

### Task 2: Add download and folder-import operations

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/QwenSemanticModelManager.kt`
- Create: `app/src/test/java/me/rerere/rikkahub/ui/pages/setting/components/QwenSemanticModelDownloadTest.kt`

**Interfaces:**
- Produces `suspend fun importBundleFromTree(context: Context, kind: ModelKind, treeUri: Uri): File`.
- Produces `suspend fun downloadBundle(context: Context, client: OkHttpClient, kind: ModelKind, onFileDone: (String, Int, Int) -> Unit = { _, _, _ -> }, onProgress: (Int) -> Unit = {}): File`.
- Produces `downloadUrls(kind: ModelKind): List<Pair<String, String>>`.

- [ ] **Step 1: Define the Hugging Face file URLs.**

  Use the model repositories already selected for the integrations:

  ```kotlin
  private const val EMBEDDER_REPOSITORY =
      "https://huggingface.co/litert-community/Qwen3-Embedding-0.6B-LiteRT"
  private const val RERANKER_REPOSITORY =
      "https://huggingface.co/litert-community/Qwen3-Reranker-0.6B-LiteRT"
  ```

  Build resolve/download URLs for each required filename through the same
  Hugging Face URL normalization used by `ModelInstall`. Keep filenames
  explicit so the status validator and downloader share one source of truth.

- [ ] **Step 2: Implement safe bundle downloads.**

  Reuse the `ModelInstall.download` flow used by
  `LocalTtsModelManager.downloadBundle`. Download into a temporary staging
  directory under the target model parent, validate the complete staged bundle,
  and only then replace or promote the target directory. This preserves a
  previous `Ready` installation if any file fails or is cancelled.

- [ ] **Step 3: Implement folder import.**

  Reuse `DocumentFile.fromTreeUri` and the existing bundle-import behavior.
  The current Qwen runtime accepts `java.io.File` paths, not persisted tree
  URIs, so copy the required files from the selected folder into a staging
  directory under the app-managed model directory. Validate before promotion.
  Do not modify the configured path on missing or unreadable files.

- [ ] **Step 4: Add operation tests.**

  Test the non-network promotion/validation boundary with temporary files:

  ```kotlin
  @Test fun failedBundleDoesNotRemoveExistingReadyFiles()
  @Test fun successfulBundlePromotesOnlyAfterAllFilesValidate()
  ```

  Keep actual HTTP and Android `DocumentFile` interaction out of JVM unit
  tests; cover those paths through the existing build and manual verification.

- [ ] **Step 5: Run focused tests.**

  Run:

  ```bash
  ./gradlew :app:testDebugUnitTest --tests '*QwenSemanticModel*Test'
  ```

  Expected result: validation and promotion tests pass.

### Task 3: Add setup state and settings integration

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/QwenSemanticModelSetupViewModel.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/ViewModelModule.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingSearchDetailPage.kt`

**Interfaces:**
- Produces `val embedderStatus: StateFlow<ModelStatus>`.
- Produces `val rerankerStatus: StateFlow<ModelStatus>`.
- Produces `val activeOperation: StateFlow<SetupOperation?>`.
- Produces `val errorMessage: StateFlow<String?>`.
- Produces `fun refresh(settings: Settings)`.
- Produces `fun download(kind: ModelKind)`.
- Produces `fun chooseFolder(kind: ModelKind, uri: Uri)`.
- Produces `fun clearError()`.

  The view model constructor is:

  ```kotlin
  class QwenSemanticModelSetupViewModel(
      private val context: Context,
      private val settingsStore: SettingsStore,
      private val httpClient: OkHttpClient,
  ) : ViewModel()
  ```

  Setup progress and errors are transient `StateFlow` values. Do not add new
  persisted fields to `PreferencesStore`.

- [ ] **Step 1: Define transient setup state.**

  Add `SetupOperation(kind, completedFiles, totalFiles, percent)` and expose
  independent status flows. The view model must use `SettingsStore.settingsFlow`
  as the source of the configured `modelDir` values and refresh status after
  startup, download completion, import completion, and settings changes.

- [ ] **Step 2: Wire downloads and imports through the view model.**

  Inject `Context`, `SettingsStore`, and the existing shared `OkHttpClient`.
  On a successful operation, update only the matching option in
  `settings.searchServices`, preserving each option's `id`, documents, and
  reranker instruction. On failure, keep the old option unchanged and expose a
  user-facing error string.

- [ ] **Step 3: Register the view model.**

  Add `viewModelOf(::QwenSemanticModelSetupViewModel)` to
  `ViewModelModule.kt`, following the existing `SettingVM` registration.

- [ ] **Step 4: Connect `SettingSearchDetailPage`.**

  Obtain the setup view model with `koinViewModel()`. Render the shared setup
  card only when the selected provider is `QwenEmbedderOptions` or
  `QwenRerankerOptions`. Pass both Qwen options from `settings.searchServices`
  so both rows remain visible regardless of which provider detail page opened
  the card. Keep the existing provider editor and test section intact.

- [ ] **Step 5: Verify serialized settings are unchanged.**

  Add or extend a settings serialization test to round-trip both Qwen option
  types with their existing fields and assert that setup updates only
  `modelDir`. Run:

  ```bash
  ./gradlew :app:testDebugUnitTest --tests '*Preferences*Test' --tests '*Qwen*Test'
  ```

### Task 4: Build the shared Material 3 Expressive setup card

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/QwenSemanticModelSetupCard.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingSearchDetailPage.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: locale resource files through the repository's localization workflow

**Interfaces:**
- Produces `@Composable fun QwenSemanticModelSetupCard(...)` with independent
  embedding and reranking rows.
- Consumes `ModelStatus`, `SetupOperation?`, and callbacks for download,
  folder-picker launch, and error dismissal.

- [ ] **Step 1: Add user-facing string resources.**

  Add sentence-case resources for the card title, explanation, model row
  labels, `Ready`, `Not installed`, `Incomplete`, `Download`, `Choose folder`,
  progress, retry, missing-file guidance, and setup errors. Keep filenames in
  diagnostics rather than the primary labels. Update supported locales through
  the existing locale-TUI process rather than hardcoding translated strings in
  Compose.

- [ ] **Step 2: Implement the shared card.**

  Use one `Card` with a clear title and two independent model rows. Each row
  must:

  - Show a status icon and text that remain meaningful without color.
  - Explain what the model does in one short sentence.
  - Show missing filenames only for `Incomplete` status.
  - Show **Download** and **Choose folder** for `Not installed` and
    `Incomplete`.
  - Disable both actions for that row while its operation is active.
  - Keep actions at least 48 dp and preserve text labels.
  - Show progress without blocking the other row.

- [ ] **Step 3: Apply restrained expressive styling.**

  Use existing `CustomColors`, `MaterialTheme.colorScheme`, typography, and
  card conventions. Use container color and shape contrast for hierarchy, not
  hardcoded colors. Use `animateContentSize` or equivalent only for small state
  changes, and ensure the layout remains usable when motion is reduced.

- [ ] **Step 4: Wire Android folder pickers.**

  Add one `OpenDocumentTree` launcher that stores the pending `ModelKind` in
  local UI state. Route the returned URI to
  `setupViewModel.chooseFolder(pendingKind, uri)`, then clear the pending kind.
  Handle cancellation as a no-op.

- [ ] **Step 5: Verify compact and expanded layouts.**

  Check that rows do not overflow at compact phone width and that buttons,
  progress, and diagnostics remain readable at a larger window width. Confirm
  content descriptions exist for status icons and that status meaning does not
  depend on color.

### Task 5: Integrate readiness with existing Qwen provider behavior

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingSearchDetailPage.kt`
- Create: `app/src/test/java/me/rerere/rikkahub/ui/pages/setting/components/QwenSemanticModelSetupStateTest.kt`

- [ ] **Step 1: Keep provider configuration independent.**

  When the embedding row becomes ready, update only
  `QwenEmbedderOptions.modelDir`. When the reranking row becomes ready, update
  only `QwenRerankerOptions.modelDir`. Do not automatically select or remove a
  provider.

- [ ] **Step 2: Keep inference errors structured.**

  Keep the existing nullable-model behavior when a model becomes unavailable
  after the card reports ready. The current structured tool or search error
  remains the inference boundary; do not surface a raw stack trace in the
  settings UI.

- [ ] **Step 3: Add status transition tests.**

  Cover independent transitions:

  ```kotlin
  @Test fun embedderReadyDoesNotMarkRerankerReady()
  @Test fun rerankerReadyDoesNotChangeEmbedderDirectory()
  @Test fun incompleteModelKeepsExistingOptionDirectory()
  ```

### Task 6: Full verification and cleanup

**Files:**
- Modify only files required by failing verification or formatting.

- [ ] **Step 1: Run focused unit tests.**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests '*QwenSemanticModel*Test'
  ```

  Expected result: all setup validation, promotion, and state tests pass.

- [ ] **Step 2: Compile the Android app.**

  ```bash
  ./gradlew compileDebugKotlin
  ```

  Expected result: `BUILD SUCCESSFUL`; pre-existing deprecation warnings may
  remain, but no new compiler errors are acceptable.

- [ ] **Step 3: Run the broader app unit-test suite.**

  ```bash
  ./gradlew test
  ```

  Expected result: all existing and new tests pass.

- [ ] **Step 4: Perform manual UI verification.**

  Verify the following on a compact phone and a larger window:

  - Both rows appear from either Qwen provider detail page.
  - Ready, not-installed, and incomplete states are distinguishable without
    color.
  - Download and folder actions work independently.
  - A failed operation preserves an existing valid installation.
  - Missing-file guidance names the actual missing files.
  - Actions meet 48 dp targets and remain readable with larger text.
  - Reduced-motion settings do not leave stale or inaccessible UI.

- [ ] **Step 5: Inspect the final diff.**

  Run:

  ```bash
  git diff --check
  git status --short
  ```

  Confirm that only the Qwen semantic setup implementation, tests, localized
  strings, and this plan/spec are included.
