# Local Dream — On-Device Image Generation

## Overview

Local Dream provides **on-device Stable Diffusion image generation** using Qualcomm Snapdragon NPU acceleration (via QNN SDK) or CPU fallback (via Alibaba MNN). It runs as a separate native C++ process (`libstable_diffusion_core.so`) that RikkaHub communicates with over HTTP/SSE on `127.0.0.1:8081`.

No JNI bridge — RikkaHub spawns the native executable via `ProcessBuilder` and talks to it via HTTP.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   RikkaHub                          │
│                                                     │
│  LocalDreamProvider.kt                              │
│    ├── ProcessBuilder → libstable_diffusion_core.so  │
│    ├── POST /generate (JSON)                        │
│    └── SSE: event: progress | event: complete        │
│                                                     │
│  SettingLocalDreamPage.kt                           │
│    └── Config UI for model/params/backend            │
└─────────────────────────────────────────────────────┘
         │
         ▼  HTTP (cpp-httplib) on 127.0.0.1:${port}
┌─────────────────────────────────────────────────────┐
│              local-dream (C++)                      │
│                                                     │
│  Backends:                                          │
│    sd15npu  → Qualcomm QNN (Hexagon V68+)          │
│    sd15cpu  → Alibaba MNN (CPU)                    │
│    sdxl     → Qualcomm QNN (SD8G3+)                │
│    anima    → Qualcomm QNN (DiT)                   │
│                                                     │
│  Pipelines:                                         │
│    PipelineSd15Npu, PipelineSd15Cpu, PipelineSdxl,  │
│    PipelineAnima                                    │
│                                                     │
│  Schedulers: Euler, Euler A, DPM++, LCM, FlowMatch  │
└─────────────────────────────────────────────────────┘
```

## Provider Settings

Defined in `ProviderSetting.LocalDream` (`ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt:436-478`).

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `modelId` | String | `"anythingv5"` | HuggingFace model ID for download |
| `width` | Int | `512` | Output width (px, must be multiple of 8) |
| `height` | Int | `512` | Output height (px, must be multiple of 8) |
| `steps` | Int | `20` | Diffusion steps |
| `cfg` | Float | `7.0` | Classifier-free guidance scale |
| `backendType` | String | `"sd15npu"` | One of: `sd15npu`, `sd15cpu`, `sdxl`, `anima` |
| `port` | Int | `8081` | HTTP server port |

Stable provider ID: `11111111-aaaa-bbbb-cccc-000000000003` (`LOCAL_DREAM_PROVIDER_ID`)

## HTTP API

### POST `/generate`

The image generation endpoint. Accepts JSON body, returns SSE stream.

**Request Body:**

```json
{
  "prompt": "a cat on a bicycle",
  "negative_prompt": "blurry, low quality",
  "steps": 20,
  "cfg": 7.0,
  "width": 512,
  "height": 512,
  "seed": -1,
  "output_format": "png"
}
```

**Fields:**

| Field | Type | Required | Default | Notes |
|-------|------|----------|---------|-------|
| `prompt` | string | yes | — | Text prompt |
| `negative_prompt` | string | no | `""` | Negative prompt |
| `steps` | int | no | `20` | Diffusion steps |
| `cfg` | float | no | `7.5` | CFG scale |
| `width` | int | no | `512` | For sd15 backends; sdxl/anima force 1024 |
| `height` | int | no | `512` | For sd15 backends; sdxl/anima force 1024 |
| `seed` | uint | no | time-based hash | Set to -1 for random |
| `scheduler` | string | no | `"dpm"` | `"euler"`, `"euler_a"`, `"dpm"`, `"lcm"`, `"flow_match"` |
| `output_format` | string | no | `"raw"` | `"png"`, `"jpeg"`, or `"raw"` |
| `preview_format` | string | no | `"raw"` | Same options as output_format |
| `show_diffusion_process` | bool | no | `false` | Emit preview per step |
| `show_diffusion_stride` | int | no | `1` | Preview every N steps |
| `image` | string (base64) | no | — | Base64 encoded img2img source |
| `mask` | string (base64) | no | — | Base64 encoded inpainting mask |
| `denoise_strength` | float | no | `0.6` | Img2img denoise strength |
| `ultrafix` | bool | no | `false` | Tiled img2img for high-res upscale |
| `tile_size` | int | no | `512` | Ultrafix tile size (sd15 only) |
| `aspect_ratio` | string | no | — | e.g. `"16:9"` (sdxl/anima only) |

**SSE Response:**

```
event: progress
data: {"type":"progress","step":1,"total_steps":20,"image":"<base64>","format":"png"}

event: complete
data: {"type":"complete","image":"<base64>","format":"png","seed":12345,"width":512,"height":512,"generation_time_ms":4231,"first_step_time_ms":1250}

event: error
data: {"type":"error","message":"Invalid JSON"}
```

### GET `/health`

Returns `200 OK` if the server is alive.

### POST `/tokenize`

Tokenizes a prompt without generating. Useful for previewing token count.

**Request:**

```json
{"prompt": "a cat on a bicycle"}
```

**Response:**

```json
{"count": 12, "max_length": 77, "overflow_offset": 0}
```

For Anima backends, `max_length` is 512 (Qwen T5 tokenizer).

### POST `/upscale`

Binary protocol endpoint for image upscaling. Body is raw RGB bytes.

**Headers:**

| Header | Description |
|--------|-------------|
| `X-Image-Width` | Input image width |
| `X-Image-Height` | Input image height |
| `X-Upscaler-Path` | Path to upscaler model file |
| `X-Use-OpenCL` | `"true"` or `"1"` for MNN OpenCL |

**Response:** JPEG bytes with headers `X-Output-Width`, `X-Output-Height`, `X-Duration-Ms`.

## Backends

| Backend | Engine | Model Format | Hardware | Notes |
|---------|--------|-------------|----------|-------|
| `sd15npu` | Qualcomm QNN | `.bin` (QNN) | Hexagon V68+ | NPU-accelerated SD1.5 |
| `sd15cpu` | Alibaba MNN | `.mnn` | Any CPU | CPU fallback for SD1.5 |
| `sdxl` | Qualcomm QNN | `.bin` (QNN) | SD8G3+ | NPU-accelerated SDXL |
| `anima` | Qualcomm QNN | `.bin` (QNN) | SD8G3+ | DiT model (16-ch VAE) |

### Model File Layout

Each backend expects a fixed file layout under `--model_dir`:

**sd15cpu / sd15npu:**
```
tokenizer.json
clip_v2.mnn
pos_emb.bin
token_emb.bin
unet.mnn (cpu) or unet.bin (npu)
vae_encoder.mnn/.bin  (optional, enables img2img)
vae_decoder.mnn/.bin
```

**sdxl:**
```
tokenizer.json
clip.mnn
clip_2.mnn
pos_emb.bin / pos_emb_2.bin
token_emb.bin / token_emb_2.bin
unet.bin
vae_encoder.bin  (optional)
vae_decoder.bin
```

**anima:**
```
tokenizer.json
tokenizer_t5.json
clip.bin
token_emb.bin
unet_part1.bin
unet_part2.bin
vae_decoder.bin
vae_encoder.bin  (optional)
```

## Lifecycle

The native process is started lazily on first image generation request via `ensureBackendRunning()`:

```
LocalDreamProvider.generateImage()
  └─ ensureBackendRunning(settings)
       └─ startBackendProcess(settings)
            ├─ Resolves libstable_diffusion_core.so from nativeLibraryDir
            ├─ Constructs args: --type, --model_dir, --port, --lib_dir
            └─ ProcessBuilder.start()
```

The process stays alive across generation requests. It is killed when RikkaHub's process terminates (child process cleanup).

The QNN runtime libraries (`libQnnHtp.so`, `libQnnSystem.so`) are expected at `context.filesDir/runtime_libs/` — this path is passed via `--lib_dir`.

## Integration Points

| File | Purpose |
|------|---------|
| `app/src/main/java/me/rerere/rikkahub/data/ai/LocalDreamProvider.kt` | Provider implementation: process mgmt, HTTP client, SSE parsing |
| `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt` (line 436) | `LocalDream` data class with config fields |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingLocalDreamPage.kt` | Settings UI: model ID, params, backend type, port |
| `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` (line 337) | Provider registration in DI graph |
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` (line 481) | Navigation route registration |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt` (line 340) | Entry point from main settings |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigure.kt` | Provider-agnostic config overrides |

## Provider Lifecycle Callbacks

`ProviderConfigure.kt` handles these LocalDream-specific cases at compile time:

| ProviderConfigure callback | Returns | Why |
|---------------------------|---------|-----|
| `baseUrl()` | `""` | On-device, no remote base URL |
| `apiKeyName()` | `""` | On-device, no API key |
| `apiKeyMaskedLabel()` | `""` | No key to display |
| `resetBaseUrl()` | `this` | No-op |
| `hasBaseUrl()` | `true` | Always considered configured |
| `doctorCheckEnabled()` | `p.enabled` | On-device, no API key check |

## Source Project

The local-dream native project lives at `/home/myrqyry/MQR/local-dream`:
- **Author:** xororz
- **Package:** `io.github.xororz.localdream`
- **License:** GPL-3.0
- **Native source:** `app/src/main/cpp/src/`
- **Latest version:** 2.8.1

## Stable Diffusion.cpp Inspiration

For future improvements, [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) offers several adaptable ideas:

| Idea | LocalDream Relevance | Notes |
|------|---------------------|-------|
| GGML/GUF model format | Wider model ecosystem | Would require new backend; no NPU support |
| Flash attention | Faster attention compute | Engine-neutral; implement in MNN/QNN ops |
| Cache modes (EasyCache) | Repeated sampling speedup | Premature; implement when multi-batch lands |
| Preview callback pattern | Already partially supported | SSE preview already works per-step |
| Hires fix / tiling | Already has `ultrafix` | LocalDream tiling is production-grade |

LocalDream's key differentiator: **Qualcomm NPU acceleration** via QNN SDK — no other on-device SD project offers this on Android.
