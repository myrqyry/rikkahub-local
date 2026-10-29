# Neumorphic lighting polish — 2026-10-29

## Intent and sources

User requested improving RikkaHub's existing neumorphic styling and supplied:
- Screenshot plus [Neumorphic Elements](https://codepen.io/myacode/pen/PoqQQNM) — light, directional double shadows, sculpted segmented tab, shallow form wells, controlled accent
- [Neumorphic Buttons](https://codepen.io/dev_loop/pen/XWJeKNO) — transient raised→pressed inset relief, asymmetric top/bottom highlights
- User-provided archives `neumorphic-elements.zip`, `neumorphic-buttons.zip`, `css-neumorphism_animation.zip`, and `editable-neumorphic-text.zip`, examined in their original CSS/SCSS/JS forms

All four uploaded examples include MIT license text; the existing vendored `neumorphic-ui` library also carries its own MIT license. We extracted **techniques**, not source code or bundled animations/assets. The original design sources remain untouched.

### Transferable mechanisms

- Raised elements rely on paired soft shadows; pressed/inset elements place complementary light/dark shading *at their inner edges*, rather than covering a whole surface.
- Selected controls should combine physical relief, a restrained accent hint, and an accessible semantic state; accent color alone must not carry the information.
- Active buttons transition to a recessed state; this already exists in our `NeumorphicButton` and was deliberately preserved.
- Animated growth from `css-neumorphism_animation` is an ornamental, continuously looping demo, not suitable for chat controls; editable embossed text can reduce legibility and was not imported.
- The CodePens' `:focus { outline: none }` and programmatic infinite animations were **not** transferred.

## Verified problems in the current Compose source

1. `NeumorphicShadowModifier.neumorphicInsetInternal` used two translucent full-surface gradient fills, causing center darkening and flattened depth, notably in selected conversation rows and the segmented track.
2. Small surfaces could get oversized gradient spans compared to their dimensions; a requested depth of 0 still permitted a positive minimum stroke.
3. A dynamic light-path already existed but filled instead of outlining the well; static and dynamic paths therefore both affected the entire center.
4. `NeumorphicSegmentedControl` selected indicator used exactly the unselected surface color, relying almost entirely on an elevation shadow; disabled tab clicks weren't explicitly gated; selection was not exposed on each tab.

## Changes in this focused pass

- Added `insetStrokeWidthPx`: finite/zero guard and bound a rim to at most 32% of the shorter side, independent of whether the user is in light or dark mode.
- Both static Compose gradients and directional-light native paints now use stroke outlines clipped to the exact surface shape; the circle branch explicitly renders a circular rim rather than a round rectangle.
- The rim is drawn **before** content, keeping type/icons crisp. The stroke geometry is calculated/cached with `drawWithCache`, not recreated each draw.
- The existing directional-light shader matrices, palette semantics, raised shadows, depth tokens and public surface API remain intact.
- The segmented-control active pill uses a restrained `lerp(surface, accentPrimary)` tint (light/dark tuned) without a hard accent border. The selected tab receives the correct `Role.Tab` and `selected` semantics; disabled controls no longer dispatch clicks.
- No app settings, installed model state, provider configurations, or external assets changed.

## Evidence and boundaries

- TDD regression `:neumorphic-ui:testDebugUnitTest --tests dev.neumorphic.NeumorphicLightingPolishTest` was RED for the missing rim-bounds and active-segment logic; the same test passed after implementation.
- `./gradlew :neumorphic-ui:testDebugUnitTest :ai:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --offline` **passed** on the connected Linux Mint development PC.
- This validates compilation and pure contracts, **not pixel fidelity or animation performance** on the Pixel. No app reinstall or APK publication was performed.
- Manual next checks after installing: light/dark app theme; selected conversation legibility; UI Preferences segmented tabs; enabled/disabled navigation taps; selected tab softened accent and raised relief; portrait/landscape on narrow screens; check for clipping in raised surfaces.
- The current app uses these shared surfaces in the chat drawer, conversation list, drawer panels, theme choice cards, and UI preferences segmented control. Full-app global restyling would be a separate design decision.

## Safe continuation

Only apply more depth to deliberately tactile interactive components. Do not turn every divider/card into a raised surface: neumorphism loses useful hierarchy when every surface is equally raised. Prefer accessibly contrasted text, labeled state, visible focus, no gratuitous motion, and bounded shadow work for battery-friendly Android scrolling.
