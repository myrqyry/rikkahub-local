<div align="center">

<img src="docs/icon.png" width="104" height="104" alt="RikkaHub Local" />

# RikkaHub Local

**A local-first Android AI workspace for models, assistants, skills, tools, automation, and multimodal creation.**

RikkaHub Local is an experimental fork of [RikkaHub](https://github.com/rikkahub/rikkahub) that expands the original multi-provider chat client into a capability-aware agent runtime. It can use cloud or on-device models, operate Android and connected tools with explicit permission, import reusable skills and plugins, process documents and media, and automate work without hiding what it is doing.

<p>
  <img src="https://img.shields.io/badge/status-active%20development-orange?style=flat-square" alt="Active development" />
  <img src="https://img.shields.io/badge/platform-Android%208%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8+" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin and Jetpack Compose" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-see%20LICENSE-blue?style=flat-square" alt="License" /></a>
</p>

<a href="https://myrqyry.github.io/rikkahub-local/">Project site</a> ·
<a href="#what-rikkahub-local-is">Overview</a> ·
<a href="#features">Features</a> ·
<a href="#project-status">Status</a> ·
<a href="#building-from-source">Build</a>

</div>

> [!IMPORTANT]
> RikkaHub Local is under rapid development and does not currently publish GitHub Releases. There is no supported release APK yet. Build from source, expect interfaces and storage formats to evolve, and do not treat the current repository as a finished consumer product.

---

## What RikkaHub Local is

RikkaHub Local is not simply “RikkaHub with more tools.” The project is being reorganized around a small set of concepts that can compose without every feature inventing its own miniature universe:

```text
Models think.
Prompts guide.
Skills instruct.
Tools act.
Assistants combine them.
```

An assistant can select a model, apply reusable instructions and knowledge, invoke only the tools it has been allowed to use, delegate work to sub-agents, and run locally or through connected providers. The goal is to let useful ecosystems feed one Android workspace instead of forcing users to learn a different installation ritual for every model, skill, plugin, or agent format.

### Design priorities

- **Local-first, not local-only.** Prefer on-device execution where it is practical, while keeping cloud providers available when they are more capable.
- **Capability-aware models.** Chat, vision, OCR, image generation, speech, embeddings, and tools are distinct capabilities rather than vague model labels.
- **Explicit authority.** Sensitive tools remain opt-in, approval-gated, and bounded by hard safety rules.
- **Interoperable artifacts.** Skills and plugins are inspected, normalized, reviewed, and installed with provenance instead of blindly copied into place.
- **Graceful degradation.** A feature should explain what is missing or incompatible instead of silently choosing an unrelated fallback.
- **One coherent workspace.** Models, prompts, skills, MCP servers, plugins, automation, files, and assistants should feel like parts of the same system.

---

## Screenshots

<p align="center">
  <img src="docs/img/chat.png" width="31%" alt="RikkaHub Local chat" />
  <img src="docs/img/assistants.png" width="31%" alt="Assistant configuration" />
  <img src="docs/img/models.png" width="31%" alt="Model configuration" />
</p>

> The UI is actively being reorganized. Screenshots may lag behind the current branch because software apparently changes faster than humans remember to retake promotional images.

---

## Features

### Cloud and on-device models

Use the original RikkaHub provider ecosystem alongside local Android runtimes.

- Cloud providers including OpenAI-compatible endpoints, OpenRouter, Google, Anthropic, Codex, Grok, and others supported by the upstream provider layer
- On-device LiteRT language and vision models
- On-device llama.cpp GGUF chat models through Llamatik
- Pixel AICore / Gemini Nano integration where supported
- Experimental local Stable Diffusion GGUF management through `stable-diffusion.cpp`
- Curated local model catalogs that link to source and licensing information
- Manual model import from local files or supported URLs
- Separate model capabilities for chat, reasoning, tools, vision, OCR, document analysis, image generation, image editing, speech, embeddings, and reranking
- A registry and resolver that preserve local/cloud boundaries and avoid silent cloud fallback
- A unified Models page backed by `ModelRegistry` for role-based assignment, capability browsing, and provider management

The unified Models page assigns models to roles (chat, vision, OCR, image generation, image editing, embeddings), filters inventory by capability, groups by provider, and supports deep links from feature screens. Per-assistant model-role overrides (including separate speech provider overrides) let one assistant use a different image or vision model than the chat default, and cloud attachment and image-processing privacy toggles gate which content may reach cloud models.

### Assistants and prompt library

Assistants combine model choices, instructions, knowledge, tools, permissions, and behavior.

- Per-assistant model and tool configuration
- Reusable mode injections and lorebooks
- Quick messages for frequently used requests
- A unified Prompt Library surface for instructions and quick messages
- Focused sub-agents with isolated context and cancellable parallel work
- Assistant-specific safety and capability boundaries

### Skills, plugins, and shared artifact imports

RikkaHub Local can absorb reusable agent artifacts instead of treating every external ecosystem as a documentation link.

- First-class Skills and Plugins pages
- Skill import from raw URLs, Markdown, local files, ZIP files, Android sharing, and GitHub directories containing `SKILL.md`
- Plugin import from supported GitHub repositories and prepared archives
- Shared candidate inspection and confirmation before installation
- Source provenance and content hashes stored after successful installation
- Bounded downloads, redirect validation, UTF-8 validation, archive traversal protection, size and entry limits, safe staging, and atomic activation
- Plugin commands and tools exposed through the existing assistant/tool system
- Compatibility handling for native RikkaHub skills and selected external skill formats

Direct catalog adapters for more ecosystems are planned. The current import foundation is deliberately source-adapter based so ClawHub, LobeHub, Hugging Face, and other catalogs can be added without creating another unrelated installer for each one. Catalog providers resolve entries into the same import pipeline (`ImportRequest` with optional pinned SHA-256), so catalog-sourced artifacts get the same staging, review, provenance, and content-hash guarantees as any other import.

### Android tools and device control

Assistants can use a broad collection of native Android tools when the user enables them.

Capabilities include screen interaction, screenshots, app launching, notifications, battery and network state, brightness and volume, contacts and messages, location and sensors, clipboard operations, NFC, Android Keystore operations, archive management, storage access, and other device functions.

Tool availability is permission-aware and assistant-specific. Without enabled local tools, the app can still behave like a conventional RikkaHub chat client.

### Workflows and scheduled jobs

Create persistent automation from plain-language intent.

- Event-driven workflows for Wi-Fi, Bluetooth, charging, notifications, screen state, app activity, time, location, and other Android signals
- Conditional execution based on device state and context
- Scheduled jobs for one-time or recurring tasks
- Runtime AI decisions or preconfigured fixed actions
- Reboot-aware scheduling and background execution support
- External automation intents for Tasker, ADB, and other Android automation systems

### Browser, workspace, Termux, and SSH

Give assistants controlled access to environments beyond the chat window.

- In-app browser with agent-driven navigation and page extraction
- Screenshot feedback and page-difference context after actions
- Workspaces for project and file context
- Termux command execution and package access
- Native SSH connections for commands, files, logs, and remote maintenance
- MCP servers for externally supplied tools and resources
- Web-server and remote-control integrations

### Files, documents, search, and knowledge

RikkaHub Local can turn local and attached content into model context.

- File browsing, reading, creation, copying, moving, renaming, deletion, and archive operations
- Search and RAG configuration
- Document-to-prompt parsing for PDF, DOCX, PPTX, EPUB, XLSX, and CSV content
- Markdown table conversion for spreadsheets
- On-device vision models for image understanding and OCR-oriented tasks
- Embedding model support for retrieval workflows
- Storage inspection, backup, restore, and chat-attachment cleanup

### Speech and audio

Use cloud or local speech components independently from the active chat model.

- Whisper-based speech recognition with configurable local sampling options
- Local Pocket TTS, Kitten TTS, and Qwen3 TTS integrations
- Cloud TTS providers and expanded provider-specific voice selections
- Voice-note and audio handling through chat and remote integrations
- Separate speech-to-text and text-to-speech configuration

### Chat-native multimodal tools

Assistants can analyze, read, generate, and edit images directly from a conversation, independent of the active chat model's capabilities.

- `analyze_image`, `extract_text_from_image`, `generate_image`, and `edit_image` agent tools
- Explicit `image_ref` inputs that accept artifact IDs, `file://`/`content://` URIs, or absolute paths
- Each tool resolves its model through the capability registry and respects the assistant's cloud-processing privacy policy
- Inline result cards with Open, Use as reference, Edit, Open in Image Studio, and Share actions
- Generated images are saved and registered in Image Studio, so they can be passed to other tools or shared onward

### Android sharing

RikkaHub Local participates in the Android share ecosystem in both directions.

- Receiving shared text, URLs, and files, with recognized skills/plugins routed to the import preview and ordinary content to the composer
- Sending text, URLs, or a single file-backed artifact (including generated images) through the Android sharesheet
- Assistant-triggered shares request approval with a preview; direct user shares open the chooser without extra prompts
- Outbound files are exposed as `content://` URIs with read-permission grants — never raw filesystem paths

### Telegram and remote interaction

A configured Telegram bot can act as a remote front end for an assistant.

- Text, image, document, and voice-note input
- Approval buttons for sensitive actions
- Interactive questions when the assistant needs user input
- Long-output delivery as files
- User allowlisting and default-chat controls
- Remote diagnostics through supported commands

### Doctor and diagnostics

The built-in Doctor audits important parts of the installation and can surface repair actions.

Checks cover permissions, background services, storage, databases, network configuration, Termux integration, and other runtime dependencies. Request logs and developer tools remain available for deeper troubleshooting.

### Safety and privacy

The agent layer is powerful enough that “the model probably meant well” is not an acceptable security policy.

1. **Per-assistant access:** tools and capabilities are enabled explicitly.
2. **Per-call approval:** sensitive actions can require confirmation before execution.
3. **Hard safety floor:** destructive command patterns remain blocked regardless of model intent.
4. **Import inspection:** external artifacts are staged, validated, reviewed, and recorded before activation.
5. **Credential handling:** secrets are kept out of ordinary logs and excluded from normal backup paths.
6. **Local/cloud separation:** model resolution does not silently upload work to a cloud provider when local-only behavior is expected.

---

## Example requests

What works depends on the models, permissions, integrations, and tools you enable, but the intended interaction is plain language rather than manual wiring:

> “Every weekday morning, summarize selected notifications and send the result to Telegram.”
>
> “Find the spreadsheet with last month’s expenses and turn the first sheet into a readable table.”
>
> “Use the local OCR model to extract this screenshot, then ask my chat model to explain it.”
>
> “Check my home server over SSH and notify me if disk usage is above 90 percent.”
>
> “Import this GitHub skill, show me what files and permissions it contains, then add it to my coding assistant.”
>
> “Open the router admin page, inspect connected devices, and ask before changing anything.”
>
> “Generate a picture of the mascot, edit out the background, and share it to my gallery.”
>
> “Analyze this screenshot with the vision model and tell me what changed.”

---

## How the pieces fit

```text
User / Telegram / Android intent
              │
              ▼
          Assistant
     ┌────────┼─────────┐
     │        │         │
   Model    Prompt    Memory
     │      Library   / RAG
     │
     ├── Skills ───────────── reusable expertise and workflows
     ├── Tools ────────────── Android, browser, files, SSH, Termux
     ├── MCP servers ──────── external tools and resources
     ├── Plugins ──────────── commands, tools, and hooks
     └── Sub-agents ───────── isolated delegated work
```

Model selection uses capability-based resolution with explicit source policies:

```text
assistant override
    ↓
conversation override
    ↓
global assignment
    ↓
first enabled compatible model
    ↓
clear “no compatible model” state
```

An invalid assistant override fails loudly instead of silently falling through, and a `ModelSourcePolicy` (`ANY` vs `LOCAL_ONLY`) is applied at every candidate level. Local models remain local unless the user has allowed an appropriate cloud fallback, and privacy checks run after resolution but before any provider call.

---

## Project status

The repository is functional but experimental. Large parts of the agent system are implemented, while the newer product architecture is still being consolidated.

### Completed foundations

- Settings reorganized around user intent
- First-class Skills, Plugins, MCP, Termux, Prompt Library, and other focused destinations
- Hardened skill and plugin imports
- Shared artifact candidate, review, provenance, and installation flow
- GitHub multi-file skill imports
- Unified Prompt Library entry point
- Local LiteRT chat and vision model catalog support
- On-device llama.cpp chat provider backed by Llamatik with GGUF streaming and cancellation
- Experimental Stable Diffusion model management and native bridge
- Local and cloud speech integrations
- Expanded document parsing, including XLSX and CSV
- Unified Models page backed by `ModelRegistry` with capability tabs, search, provider grouping, and role assignments
- Per-assistant model-role and speech overrides with cloud attachment/image-processing privacy controls
- Chat-native multimodal tools (`analyze_image`, `extract_text_from_image`, `generate_image`, `edit_image`) with inline result cards
- Catalog adapters routed through the safe-import pipeline with pinned-hash gating
- Inbound and outbound Android share (text, URLs, and single file-backed artifacts)

### In progress / next

- More catalog source adapters, including ClawHub, LobeHub, and Hugging Face flows
- Continued runtime validation and acceleration work for local image generation
- Documentation and screenshots that catch up with the implementation before it mutates again

---

## Building from source

### Requirements

- Android Studio or a compatible Android SDK installation
- JDK 17
- Android SDK 37
- Android NDK with CMake 3.22.1 support
- Git with submodule support
- An Android 8.0+ device or emulator

The project currently builds `arm64-v8a`, `x86_64`, and universal debug APKs. The application ID is `excp.rikkahub.local`, so it can coexist with upstream RikkaHub.

### Clone and build

```bash
git clone --recurse-submodules https://github.com/myrqyry/rikkahub-local.git
cd rikkahub-local
./gradlew :app:assembleDebug
```

To install directly on a connected device:

```bash
./gradlew :app:installDebug
```

Debug APKs are written under:

```text
app/build/outputs/apk/debug/
```

If the repository was cloned without submodules:

```bash
git submodule update --init --recursive
```

The native build includes the pinned `stable-diffusion.cpp` submodule and Material Color Utilities source. Local model weights are not bundled with the application.

### Run tests

```bash
./gradlew test
```

A fuller development verification pass used by recent changes is:

```bash
./gradlew test assembleDebug --no-daemon
```

---

## Device and runtime requirements

| Requirement | Current value |
|---|---|
| Android | 8.0+ / API 26 minimum |
| Target SDK | API 37 |
| Architectures | `arm64-v8a`, `x86_64`, universal APK |
| Java/Kotlin target | JVM 17 |
| UI | Jetpack Compose / Material 3 |
| Native components | LiteRT, Llamatik (llama.cpp), local speech libraries, `stable-diffusion.cpp` |
| Model storage | Downloaded or user-imported; weights are not bundled |
| Release channel | None yet; build from source |

Local AI performance varies substantially by model, device memory, thermal state, and accelerator support. A model appearing in a catalog is not proof that a particular phone can run it comfortably.

---

## Languages

The interface includes English, 简体中文, 繁體中文, 日本語, 한국어, Русский, and العربية resources. The app follows the system language and falls back to English. RTL chat rendering is supported while code blocks remain left-to-right.

---

## Contributing and issue reports

Bug reports and focused pull requests are welcome. Include the device, Android version, build/commit, model or provider involved, relevant permissions, and reproducible steps.

This is a fork with substantial agent, local-runtime, import, automation, and UI changes. Confirm that a bug also exists upstream before reporting it to the original RikkaHub project.

Because the architecture is changing quickly, additions should reuse existing registries, import coordinators, model abstractions, and safety boundaries rather than creating another isolated manager that stores the same concept differently. The codebase already has enough opportunities for duplicate state without deliberate encouragement.

---

## Credits

RikkaHub Local builds on many projects, including:

| Project | Role |
|---|---|
| [RikkaHub](https://github.com/rikkahub/rikkahub) | Upstream Android chat client, provider abstraction, and interface foundation |
| [LiteRT](https://ai.google.dev/edge/litert) | On-device model execution |
| [Llamatik](https://github.com/ferranpons/Llamatik) | llama.cpp bindings for on-device GGUF chat |
| [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) | Experimental local image-generation runtime |
| [cron-utils](https://github.com/jmrozanec/cron-utils) | Cron expression parsing |
| [whisper.cpp](https://github.com/ggerganov/whisper.cpp) | Local speech-recognition foundation |
| [Termux](https://github.com/termux/termux-app) | Android shell and package environment |
| [JSch](https://github.com/mwiede/jsch) | SSH client support |
| [FlorisBoard](https://github.com/florisboard/florisboard) | Foundation for the companion agent keyboard work |

This fork is not affiliated with the upstream RikkaHub maintainers. Credit for the original application and its provider ecosystem belongs to the upstream project and its contributors.

---

## License

See [LICENSE](LICENSE). The repository currently uses segmented dual-licensing terms: qualifying non-commercial, personal, educational, research, or small-user use is offered under AGPL v3 terms, while other commercial use requires a separate commercial license. Review the license file itself before distributing or deploying the software.
