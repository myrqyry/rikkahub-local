---
name: find-mingcute-icons
description: Use when selecting or adding an Android UI icon in RikkaHub. Search the local MingCute facade first, then verify any new upstream drawable name before adding a mapping.
---

# Find MingCute Icons

RikkaHub's Android UI uses MingCute VectorDrawables through the local facade:

`app/src/main/java/me/rerere/rikkahub/ui/icons/MingcuteIcons.kt`

The facade intentionally preserves many older semantic property names so call sites can migrate without coupling application code to raw dependency resource names.

## Workflow

1. Search the local facade before adding anything:

```bash
rg -n 'val .*<keyword>|mgc_.*<keyword>' app/src/main/java/me/rerere/rikkahub/ui/icons/MingcuteIcons.kt
```

2. If a suitable facade property exists, use it. Existing migrated files commonly keep the compatibility alias:

```kotlin
import me.rerere.rikkahub.ui.icons.MingcuteIcons as HugeIcons

Icon(HugeIcons.Search01, contentDescription = null)
```

New focused code may import `MingcuteIcons` directly when that is clearer.

3. If no facade property exists, verify the drawable in the installed `com.maifee.mingcute:mingcute-icons` artifact before adding a mapping. Resource names use the `mgc_*_line` / `mgc_*_fill` convention. Do not guess a resource name.

A useful local search is:

```bash
find ~/.gradle/caches -type f \( -name '*.aar' -o -name '*.jar' \) -path '*mingcute*' -print
```

Inspect the matching artifact/resources, then add the narrow mapping to `MingcuteIcons.kt`.

4. Prefer outline (`*_line`) icons unless the surrounding UI deliberately uses a filled state.

## Constraints

- Do not reintroduce the removed HugeIcons or Lucide dependencies just to obtain one symbol.
- Keep raw `MingCuteR.drawable.*` usage inside the facade unless a resource ID is specifically required by a non-Compose API.
- Reuse an existing semantic mapping when its meaning is close enough; avoid proliferating aliases that differ only by historical library naming.
- Verify Android compilation after adding or changing mappings.
