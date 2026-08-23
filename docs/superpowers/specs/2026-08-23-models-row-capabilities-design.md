# Models inventory row capabilities

This design makes the Models inventory row a clearer source of truth without
changing model storage, routing, or provider behavior. The row continues to
use the registry's capability data and exposes meaningful availability state
when a model cannot currently be used.

## Goals

- Show the model name and its local or cloud source together.
- Show capability icons and labels from `ModelDescriptor.capabilities`.
- Make provider and lifecycle problems visible in the inventory row.
- Preserve the existing aggregate enable/disable switch behavior.
- Keep per-capability controls on the model detail page.
- Keep the layout compact enough for phone-sized screens.

## Non-goals

- Do not add a new model or capability representation.
- Do not change model inference, selection, persistence, or provider routing.
- Do not add a second navigation path to model configuration.
- Do not change the behavior of local Stable Diffusion execution.
- Do not add capability inference to the UI.

## Current data flow

`UnifiedModelsViewModel.managerVisibleModels` exposes registered
`ModelDescriptor` values to `ModelsPage`. `ModelInventorySection` renders each
descriptor and currently delegates capability display to `ModelCapabilityRow`.
`ModelCapabilityInference` and provider metadata populate the descriptor's
capabilities; the UI must treat that set as authoritative.

## Row design

Each inventory row keeps the existing clickable `CardGroup` item:

- Headline: `model.displayName` followed by `SourceBadge`.
- Supporting content: the existing capability icon and label row, using
  `model.capabilities`.
- Status content: a compact, secondary-colored or error-colored line only when
  the model is not ready for normal use. The status covers disabled providers,
  unavailable cloud connections, and non-ready or failed local lifecycles.
- Trailing content: the existing aggregate `Switch`. It is checked when
  `enabledCapabilities` is non-empty and continues to enable or disable all
  advertised capabilities through `UnifiedModelsViewModel`.

The row does not show disabled capabilities as if they were absent. The full
advertised set remains visible, while the switch communicates the aggregate
enabled state. Users who need per-capability control open the detail page.

## Status rules

Status presentation is derived from existing descriptor fields, in this order:

1. A disabled provider takes precedence and uses the existing provider-disabled
   error string.
2. A cloud model whose provider is not connected uses the existing unavailable
   connection string.
3. A local model whose lifecycle is not `READY` uses the existing lifecycle
   label.
4. Ready, connected models have no extra status line.

No new persisted status is introduced. The status is recomputed from the
current registry state.

## Testing and acceptance

The implementation is complete when:

- The inventory row renders the registry capability set without model-name
  checks or hardcoded capability decisions.
- Local and cloud source badges remain visible.
- Disabled providers, disconnected cloud models, and non-ready local models
  show an actionable status without hiding the model.
- Aggregate enable and disable behavior remains unchanged.
- Existing model registry and capability inference tests pass.
- The app debug build succeeds.
- The layout remains readable on the phone-sized device used for verification.
