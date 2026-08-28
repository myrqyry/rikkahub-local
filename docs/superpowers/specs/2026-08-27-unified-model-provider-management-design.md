# Unified model and provider management

This design separates provider configuration from model lifecycle management.
Users browse and install models from one modality-first catalog, while the
model metadata selects the runtime and provider registration automatically.
Providers remain configurable, but the provider screen shows only providers
the user has enabled or configured.

## Goals

- Treat LiteRT as a built-in, enabled provider.
- Keep provider configuration compact and focused on provider settings.
- Let users add providers through a provider-type picker instead of scrolling
  through every supported provider.
- Present local and remote models in one inventory organized by modality.
- Place FLUX.2-klein under Image Models regardless of its LiteRT runtime.
- Route every installed local model through authoritative model metadata.
- Reuse the existing single-file installer and resumable multi-file downloader.
- Register an installed model with the correct provider automatically.
- Preserve installed model data and make failed installs recoverable.

## Non-goals

- Do not require users to choose a runtime during model installation.
- Do not allow a model to load through an incompatible runtime.
- Do not redesign provider authentication or remote provider APIs.
- Do not expose FLUX image editing controls in this change.
- Do not add a second model storage system where an existing manager applies.
- Do not delete existing providers or installed models during migration.

## Current boundaries

`ProviderConfigure` currently combines provider settings with LiteRT model
installation, download progress, installed-file management, and recommended
model links. `SettingLocalLlmViewModel` owns single-file LiteRT installation,
provider registration, and lifecycle refresh. `ModelManagerViewModel` owns
Stable Diffusion GGUF installation and classifies GGUF files that are actually
chat models into the llama.cpp provider. `QwenSemanticModelManager` already
provides the resumable, multi-file Hugging Face package flow needed by FLUX.

The implementation keeps these storage and download primitives, but moves the
user-facing entry point into the shared Models surface.

## Provider surface

The Providers screen renders a compact list of enabled or configured providers.
LiteRT is present by default and starts enabled. Each row shows the provider
name, connection or availability state, and a concise action for editing or
disabling it. Provider-specific fields appear only after the user opens that
provider.

The **Add provider** action opens a provider-type picker. Selecting a type
creates or opens that provider's focused configuration screen. The list does
not render configuration sections for providers the user has not selected.

`ProviderConfigure` no longer owns model URLs, catalog downloads, installed
model rows, or model deletion. It may retain provider-level health and local
runtime diagnostics when those diagnostics are needed to configure the
provider.

## Unified model catalog

The Models screen exposes one catalog grouped by modality rather than runtime.
The initial sections are:

- Chat Models
- Image Models
- Vision and Audio Models

Each catalog entry contains its display metadata, modality, required runtime,
installation kind, source URL or package description, and validation rules.
The UI displays the modality and install state; it does not ask the user to
select LiteRT, llama.cpp, or Stable Diffusion.

FLUX.2-klein is an Image Models entry. Its entry identifies the LiteRT
multi-file package layout and delegates installation to
`QwenSemanticModelManager`. A single-file LiteRT model delegates to the
existing `SettingLocalLlmViewModel`/`ModelInstall` path. Stable Diffusion and
GGUF entries continue to use their existing manager paths.

Installed models appear in the same modality section as their catalog entry,
with progress, ready, incomplete, failed, and unavailable states. The catalog
must not present a partially downloaded package as loadable.

## Runtime routing and registration

The catalog entry's runtime metadata is authoritative for curated models. For
user-imported files, the existing file validators and classifiers remain the
authority where classification is possible. Registration follows this flow:

1. Resolve the catalog entry or validate the imported file.
2. Select the required runtime from model metadata or classification.
3. Install into that runtime's existing storage location.
4. Validate the complete file or package before promotion.
5. Create or update the matching provider model entry.
6. Refresh the model registry and expose the installed model in its modality
   section.

The registration operation must reject an incompatible runtime/provider pair
instead of silently falling back. Existing providers remain deduplicated by
their stable identifiers, and repeated installation updates the existing model
record rather than creating duplicate entries.

## Installation and failure handling

Single-file downloads retain HTTP progress, validation, and provider
registration behavior from `ModelInstall`. Multi-file packages retain staging,
per-file resume support, metadata and SHA-256 verification when available,
atomic promotion, rollback, and engine-cache invalidation from
`QwenSemanticModelManager`.

The Models surface reports the active file or package progress and the
actionable error. Cancellation leaves resumable partial data where the current
manager supports it. Validation failure removes only the invalid staged file
or package and leaves a previously installed version intact. Provider
configuration remains usable when a model download fails.

## Testing and acceptance

The implementation is complete when:

- LiteRT appears as an enabled built-in provider on a fresh settings state.
- The Providers screen lists only enabled or configured providers.
- Adding a provider uses a type picker and opens focused configuration.
- `ProviderConfigure` contains no model installation, download, or installed
  model management controls.
- The Models screen groups entries by modality and includes FLUX.2-klein under
  Image Models.
- Curated model metadata selects the runtime without a user runtime choice.
- Incompatible runtime loading and registration are rejected.
- FLUX package validation covers required files and flat or nested layouts.
- Installation registers the model with the correct provider exactly once.
- Interrupted multi-file downloads resume, and failed promotion preserves the
  prior installed package.
- Routing, registration, catalog validation, and provider-surface ownership
  tests pass.
- The local-llm test suite passes.
- The app debug build succeeds, subject to the existing environment dependency
  required by the web module.
- Installed application data is preserved during verification.
