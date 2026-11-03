# Line MD gauge icons for the model-aware reasoning selector

**2026-10-28.** Source links supplied by user:

- https://icon-sets.iconify.design/line-md/gauge-empty/
- https://icon-sets.iconify.design/line-md/gauge-low/
- https://icon-sets.iconify.design/line-md/gauge/
- https://icon-sets.iconify.design/line-md/gauge-full/

All four are original Material Line Icons (Line MD) with the same 24×24
SVG dial/body/tick geometry and separate final needle angles. The original
SVGs animate the outer dial and then rotate the needle with SVG
`animateTransform`, but Compose ImageVector does not execute SVG animations.

Rather than adding a remote Iconify service, SVG parsing runtime, or heavy
animation library, `ui/components/ai/ReasoningGauge.kt` implements the
original final dial geometry using Android Compose Canvas and animates only
the needle on effort changes with `animateFloatAsState`. No looping
animation, no background CPU use, no network access.

| App's current reasoning level | Dial presentation | Needle degrees from original Line MD |
| --- | --- | --- |
| OFF | gauge-empty | 15° |
| AUTO | neutral dial with `A` and no needle | none (not misleadingly Medium) |
| LOW | gauge-low | 65° |
| MEDIUM | gauge | 115° |
| HIGH | gauge-full | 165° |
| XHIGH | gauge-full + distinct `+` marker | 165° |

High and Xhigh share the maximum gauge needle angle, since the source
provides only four calibrated positions, and Xhigh is separately marked.
This is **display only**, not a change to the model's thinking budget or
provider request. The existing `composerReasoningLevels(provider)` gating
continues to hide the button when a model cannot reason or has only a
single supported level. The picker itself still offers only the advertised
supported levels, and the selected icon matches the effective request
effort, not an unsupported saved preference.

The same gauge is used in the collapsed chat-composer toggle and the
selected state shown in the reasoning bottom sheet. The full picker shows
a textual label; the compact toggle has a spoken accessibility label
(e.g. Reasoning: High). The needle animates when the level changes, with
the Compose platform animation-duration scaling respected.

**Licensing:** The icon set is MIT-licensed. Copyright 2020 Vjacheslav
Trushkin. The required full original copyright/permission notice is
preserved in `docs/licenses/line-md.txt`. This adaptation does not copy
the SVG animation machinery or use the JS/Iconify CDN.

**Test and runtime evidence:** `ReasoningGaugeSpecTest` was written
RED-first and verifies all six levels, four original needle angles and
extra markers. Focused `:app:testDebugUnitTest` passed. The Android
debug build and complete app/AI JVM suites are checked before local
checkpoint. Actual pixel-perfect/composer device rendering is **not**
confirmed until an updated debug APK is installed and visually inspected.
