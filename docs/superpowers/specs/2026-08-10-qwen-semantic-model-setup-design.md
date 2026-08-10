# Qwen semantic model setup design

This design refines the search provider setup experience for the local Qwen
embedding and reranking models. It keeps the existing search-provider flow,
but gives both models a shared setup surface with independent readiness and
installation actions.

## Goals

The setup flow must:

- Explain embedding and reranking in plain language.
- Show independent status for the embedding and reranking models.
- Distinguish ready, not installed, and incomplete model files.
- Support direct Hugging Face downloads.
- Support selecting an existing local model directory with the Android folder
  picker.
- Validate selected or downloaded files before marking a model ready.
- Keep either model usable when the other model is unavailable.
- Preserve existing serialized search options and provider selection behavior.
- Use restrained Material 3 Expressive hierarchy, spacing, shape, and dynamic
  color without changing navigation patterns.

## Non-goals

This change does not redesign the entire search settings page, change model
format or inference behavior, add background model synchronization, or replace
the existing `SearchService` architecture.

## User experience

The Qwen embedding and reranking providers share one setup card in
`SettingSearchDetailPage`. The card starts with a short explanation that local
semantic models improve matching and work on-device. Technical filenames and
model internals stay behind supporting text or a secondary detail action.

The card contains two independent rows:

- **Embedding model**: Used to compare meaning between a query and documents.
- **Reranking model**: Used to reorder candidate documents by relevance.

Each row contains the model name, a status label, a short status explanation,
and actions appropriate to the current state. Actions have at least 48 dp touch
targets and retain text labels.

### Status states

Each model reports one of these states:

- **Ready**: All required files exist and pass validation. The row uses the
  primary or secondary container color sparingly to communicate availability.
- **Not installed**: No usable model directory is configured. The row offers
  **Download** and **Choose folder**.
- **Incomplete**: A directory is configured, but one or more required files are
  missing or invalid. The row identifies the missing requirement in plain
  language and offers **Download** and **Choose folder**.

The embedding and reranking rows do not block each other. A ready embedder
must remain usable while the reranker is incomplete, and vice versa.

## Installation flow

Installation and validation run outside composable functions. The UI observes
state and dispatches actions through the existing settings/view-model pattern.

### Download

When the user selects **Download**, the app downloads the required files from
the corresponding Hugging Face model page into the app-managed model
directory:

- Embedding model: `filesDir/models/embedder/`
- Reranking model: `filesDir/models/reranker/`

The flow reports progress, handles cancellation or failure without losing a
previous ready installation, and revalidates after every completed download.
The UI must not claim readiness until all required files are present and
readable.

### Choose folder

When the user selects **Choose folder**, the app opens the Android folder
picker. The selected directory is validated against the required file set for
that model. A valid directory becomes the configured model directory. An
invalid directory remains unchanged and displays the missing-file guidance.

The picker flow must preserve the existing URI permission behavior used by the
app. It must not copy files unless the current storage flow already requires a
copy; selecting a directory is sufficient when the runtime can read it.

## Required files and validation

Validation is centralized outside the UI so download, folder selection, and
startup status use the same rules. The validator checks that required files
exist, are readable, and are non-empty. It returns a structured status with
missing or invalid filenames for user-facing guidance.

The embedding model requires the LiteRT model, embedding table, tokenizer
vocabulary, and tokenizer merge files. The reranking model requires the LiteRT
model, embedding table, tokenizer vocabulary, and tokenizer merge files.

The validator must not instantiate `CompiledModel`. Runtime initialization
remains separate so a missing or incompatible accelerator does not make the
settings screen crash.

## Visual direction

Use foundational Material 3 Expressive styling:

- One clear card hierarchy instead of separate technical panels.
- Moderate corner-radius and container contrast using semantic dynamic-color
  tokens.
- Compact vertical spacing that still separates the two rows clearly.
- One emphasized action per row, with the alternate action styled as a
  secondary or text action.
- Status icons and text that remain understandable without color.
- No decorative animation beyond lightweight state transitions; honor reduced
  motion and system animation scale settings.

The screen remains optimized for compact phone windows and uses the existing
page navigation and app typography. Medium and expanded windows must not cause
the card actions or status text to overflow.

## Architecture

The change introduces a small model-setup state boundary rather than putting
file checks and downloads in `SettingSearchDetailPage`.

- A model requirement definition lists the model directory, Hugging Face
  source, and required files.
- A validator maps a directory to `Ready`, `NotInstalled`, or `Incomplete`
  with diagnostic details.
- A setup coordinator handles download and folder-selection actions, then
  emits refreshed status.
- `SettingSearchDetailPage` renders the shared card and independently observes
  the two model states.
- Existing `QwenEmbedderOptions` and `QwenRerankerOptions` remain the persisted
  provider configuration. The setup flow updates only their model-directory
  values and does not alter the serialized discriminator or provider list
  format.

The existing nullable model injection remains defensive. A model is only
available to inference tools or services when its files pass setup validation;
unavailable models return the existing structured error instead of crashing
the app.

## Error handling

The setup flow must handle download errors, picker cancellation, inaccessible
directories, missing files, empty files, and invalid persisted paths. Errors
are shown near the affected model row with an actionable next step. A failed
download must not delete a previously valid model installation.

Inference failures remain separate from setup errors and continue to use the
existing tool/search error envelopes.

## Verification

Add focused tests for:

- Required-file validation for ready, missing, incomplete, and unreadable
  directories.
- Independent embedding and reranking status transitions.
- Download failure preserving an existing ready directory.
- Folder selection accepting valid files and rejecting incomplete folders.
- Persisting the selected model directory without changing provider
  serialization.

Verify the UI manually at compact phone size and at least one larger window
size. Confirm that all actions meet 48 dp touch targets, status meaning does
not depend on color alone, and reduced-motion settings do not create a broken
transition.
