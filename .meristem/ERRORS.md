<!-- meristem-template:v1 -->
# Errors

Preserve costly, recurring, or non-obvious failures that future agents could plausibly repeat.

Use entries like:

## YYYY-MM-DD — Error / regression

**Observed failure:**  
**Root mechanism:**  
**Affected environment:**  
**Recovery / fix:**  
**How to detect earlier:**  
**Evidence / references:**

## 2026-10-13 — ThemeFamily seed packing collapses accent selection

**Observed failure:** Initial `MeristemThemeTest` failed: the new palette's `blue` and `teal` selections produced exactly the same resolved primary color despite different declared source ARGBs. The test passed after the new family began supplying raw ARGB longs.

**Root mechanism:** The pre-existing `ThemeFamily.seed(hex)` constructs `Color(hex)` and takes `.value.toLong()` (Compose packed color representation). `CustomTheme.generateColorScheme` instead interprets `primaryColorArgb.toInt()` as a 32-bit ARGB source. These are different encodings.

**Affected environment:** The new Meristem family initially, and *potentially* other existing theme families that use `seed()`. Their resulting color behavior was not individually tested and must not be silently changed here.

**Recovery / fix:** The new `meristem` family uses direct `0xFFRRGGBB` Long values and validates different accent outputs. Existing theme definitions and the `seed()` helper remain unchanged until a separate scoped palette audit considers the appearance changes and persistence implications.

**How to detect earlier:** Assert that two named accents produce different resolved `ColorScheme.primary` values, in addition to checking light/dark contrast and preset registration.

**Evidence / references:** `ui/theme/{ThemeFamily.kt,CustomTheme.kt}`, `ui/theme/MeristemThemeTest.kt`; first focused JUnit run failed at `MeristemThemeTest.kt:30`, corrected rerun passed 2/2.
