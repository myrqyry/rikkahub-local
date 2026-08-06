# Changelog

All notable changes to RikkaHub Local are documented here. RikkaHub Local does
not publish numbered releases yet; entries below are grouped by the local
implementation milestones described in `docs/superpowers/`.

## 2026-08-05 — Agent roadmap completion

Delivered the full local implementation roadmap: model capabilities, unified
Models UI, feature integrations, chat multimodal tools, catalog adapters, and
Android sharing. Every change landed through a spec → plan → test-driven
implementation flow and was verified with `./gradlew test assembleDebug`.

### Catalog adapters

- `ImportCoordinator.import(ImportRequest)` seam with `ImportOrigin.Catalog`
  and pinned SHA-256 gating (`missing_hash` / `sha_mismatch` block before
  install)
- `ArtifactCatalogProvider` / `ArtifactCatalog` / `CatalogEntry` /
  `CatalogProvenance` types and a `BundledCatalogAdapter` wrapping the bundled
  featured-skills catalog
- `SkillsVM.installFromCatalog` now routes through the import coordinator
  instead of fetching URLs directly
- Structured `ImportResult` (`Installed` / `Blocked` / `Failed`)

### Android sharing

- Inbound: `InboundShareNormalizer` classifies shared text, URLs, and files;
  `ArtifactImportRecognizer` routes recognized skills/plugins to the import
  preview and ordinary content to the composer; `SharedPayloadHandoff` passes
  payloads by id
- Outbound: single `AndroidShareService` with `ShareArtifactResolver`;
  `share` tool accepts `artifact_ref` and always asks for approval when an
  artifact is shared; image result cards gained a Share action
- Outbound files are exposed as `content://` URIs with read-permission grants,
  never raw filesystem paths

### Chat multimodal tools

- `analyze_image`, `extract_text_from_image`, `generate_image`, and
  `edit_image` agent tools, registered independent of the chat model
- Explicit `image_ref` contract accepting artifact IDs, `file://`/`content://`
  URIs, or absolute paths
- `ImageMediaStore` shared by Image Studio and the tools; generated images are
  saved and registered in Image Studio
- `ImageTextExtractor` shared between the OCR tool and the attachment
  transformer
- Inline result cards with Open, Use as reference, Edit, Open in Image Studio,
  and Share actions; canonical `ImageToolResult` envelope decoded from tool
  context

### Feature integrations

- Per-assistant model-role overrides (`modelOverrides`) and separate speech
  provider overrides
- Cloud attachment and image-processing privacy controls on assistants
- `ModelRoleResolver` + `ModelSourcePolicy` (local-first resolution, explicit
  failure on invalid overrides) applied across selectors and tools

### Unified Models UI

- Unified Models page backed by `ModelRegistry`: role assignments for chat,
  vision, OCR, image generation, image editing, and embeddings
- Capability tabs, search, provider grouping, and deep links from feature
  screens
- `RegistryModelSelector` reusable across image and assistant settings

## 2026-08-04 — Local image generation and first milestone

- Experimental local Stable Diffusion GGUF support through a
  `stable-diffusion.cpp` JNI bridge
- Curated local model catalogs and manual model import
- First milestone consolidating settings, skills, plugins, MCP, Termux, prompt
  library, and local/cloud speech

## 2026-08-01 — Local image generation design

- Spec for on-device image generation and model catalog support
