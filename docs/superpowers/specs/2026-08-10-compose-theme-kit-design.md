# Compose Theme Kit design

This design adds the approved Theme Kit behavior to RikkaHub's existing
Jetpack Compose theme system. It preserves the current Material 3 architecture,
custom-theme import format, dynamic-color behavior, and existing settings.

## Scope

The implementation adds these static theme families:

- Dracula
- Catppuccin
- Rosé Pine
- Tokyo Night
- Gruvbox Dark

Existing Sakura, Ocean, Spring, Autumn, and Black presets remain available.
Material You remains the platform dynamic-color path rather than becoming a
second CSS-style theme system.

The Android implementation intentionally excludes CSS variables, Tailwind
patterns, HTML color inputs, and browser right-click handling. Long-press is
the native equivalent for the accent shortcut.

## Theme model

`ThemeFamily` is the registry entry for a theme family. Each entry contains an
identifier, localized display name, variation definitions, and accent seed
definitions. A variation defines the family's base palette; an accent replaces
the primary seed used to generate the family palette.

The registry is the single source for available preset families. Existing
`PresetTheme` values continue to provide the final light and dark
`ColorScheme`s consumed by `RikkahubTheme`, so custom themes and existing
callers remain compatible.

The initial registry entries use these variations and accents:

- Dracula: Default, Soft, High Contrast; Purple, Pink, Cyan, Green, Orange,
  Yellow
- Catppuccin: Mocha, Macchiato, Frappé; Lavender, Blue, Sapphire, Mauve,
  Rosewater, Flamingo, Pink, Red, Maroon, Peach, Yellow, Green, Teal, Sky
- Rosé Pine: Default, Moon; Iris, Rose, Gold, Pine, Foam, Love
- Tokyo Night: Night, Storm; Purple, Blue, Cyan, Green, Orange, Red
- Gruvbox Dark: Medium, Hard, Soft; Yellow, Orange, Red, Green, Aqua, Blue,
  Purple

Material 3 color utilities generate light and dark schemes from the selected
seeds. The generated schemes must retain readable `on*` roles and use the
existing scheme conversion path. AMOLED mode remains a final surface override.

## Persistence

Add these fields to `Settings` and the existing preferences serializer:

- `themeVariation: String`, defaulting to the first variation of the selected
  family
- `themeAccent: String`, defaulting to the first accent of the selected family
- `materialYouSourceColor: Long?`, defaulting to `null`

Missing values in existing installations use the defaults. Unknown persisted
variation or accent values fall back to the selected family's first value.
Changing families resets variation and accent to that family's first values.

Custom theme JSON remains unchanged. Imported custom themes continue to use
their existing schema and do not acquire preset variation or accent fields.

## Selection behavior

Selecting a different family applies that family's first variation and accent.
Selecting the active family again cycles to its next variation and wraps at the
end. A long-press on the theme selector cycles the active family's accent and
wraps at the end.

The selector exposes the active family, variation, and accent. Variation and
accent controls also remain directly available in `SettingThemePage`, so the
same behavior is accessible without relying on a gesture.

When Dynamic Color is enabled, the current wallpaper-derived behavior remains
the default. If the user supplies a Material You source color, that color is
used as an explicit seed for the dynamic scheme; clearing it restores the
wallpaper-derived scheme. Static preset selection remains available when
Dynamic Color is disabled.

## Settings UI

`SettingThemePage` gains native Compose controls:

- A theme-family picker using the existing preview swatches.
- A variation selector shown for the active family.
- An accent selector showing the active seed name and color.
- A Material You source-color control that uses the existing Compose color
  editing approach, not an HTML input.

The controls use Material 3 components and the current app spacing, shapes,
typography, and color scheme. Selected states expose text labels and semantic
state, and all interactive targets meet the existing Compose accessibility
requirements.

## Runtime flow

1. `RikkahubTheme` reads the persisted theme family, variation, accent, and
   Material You source color.
2. Dynamic Color uses the explicit source color when present, otherwise the
   existing platform wallpaper source.
3. Static mode resolves the family registry entry and generates or retrieves
   the selected light or dark `ColorScheme`.
4. AMOLED mode applies its existing black background and surface override.
5. Settings updates persist through the existing `SettingsStore` path and
   trigger normal Compose recomposition.

## Testing

Add focused JVM tests for:

- Missing persisted fields receiving compatible defaults.
- Selecting a new family resetting variation and accent.
- Selecting the active family cycling variations with wraparound.
- Long-press accent cycling with wraparound.
- Unknown variation and accent values falling back safely.
- Static scheme selection returning distinct light and dark schemes for a
  registered family.
- Existing custom-theme JSON remaining decodable without new fields.

The implementation must also run the app module unit tests and the existing
Android build verification. No CSS, web, or browser tests are required.

## Explicit non-goals

- Background SVG pattern support from `tailwind-heropatterns`
- CSS custom-property contracts
- Browser-only right-click events
- Replacing the existing custom-theme editor or JSON format
- Changing the meaning of Dynamic Color when no explicit source color exists
