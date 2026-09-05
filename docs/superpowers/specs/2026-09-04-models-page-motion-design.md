# Models Page Motion Design

## Context and Intent

Improve the existing Models page with high-expression Material 3 motion while preserving its current layout, hierarchy, navigation, and data behavior. Motion communicates hierarchy, state, and navigation; it is not a feature of its own.

## Scope

Target phones first, with behavior that remains visually sound on wider windows and other device sizes. Do not add sections, controls, navigation destinations, or persistent state. Keep stable item identity and scroll position during all animations.

## Motion Behavior

### Provider Inventory

- Animate provider chevron rotation with a quick, low-bounce spring.
- Reveal and hide provider model rows with `AnimatedVisibility`, using vertical expand/shrink and a light fade.
- Animate status-dot colors for enabled, disabled, and error states.
- Add a restrained pressed-state response to existing clickable rows. Prefer tonal indication; use a `0.985f` scale only if visual verification shows it improves feedback without disturbing neighboring alignment.
- Do not add a perpetual pulse. A one-shot error emphasis is optional only if the color transition is insufficient for state clarity.

### Default Assignments

- Animate selected model values with `AnimatedContent`, including changes in label length.
- Keep the supporting/error slot stable so unavailable-model changes do not alter row height or make the section jump.
- Reveal the suggestion-model row with a short `AnimatedVisibility` transition when suggestions are enabled.

### Add-Model Sheet

- Animate only the content inside the existing modal sheet with directional `AnimatedContent`.
- Root to subpage moves in one direction; subpage back navigation reverses it.
- Leave the modal sheet, picker, dialog, and existing navigation behavior unchanged.

## Material and Accessibility Constraints

- Use semantic Material color tokens and preserve dynamic color behavior.
- Preserve all existing labels and content descriptions.
- Keep interactive targets at least 48dp.
- Honor Android and Compose animation-duration scale. Use instant transitions when reduced motion is enabled.
- Animate transforms, alpha, color, and content-size properties only; avoid per-frame expensive work.

## Verification

1. Build the debug APK.
2. Verify the existing Models page layout and navigation remain unchanged.
3. On a phone, verify provider expansion, row press feedback, status changes, assignment changes, suggestion toggling, and sheet forward/back motion.
4. Verify no scroll jumps, unstable item recreation, clipped expanded content, clipped shadows, or sheet-content clipping.
5. Check a wider window or tablet layout for clipping and max-width behavior.
6. Verify reduced-motion behavior and accessibility semantics.

## Out of Scope

- Redesigning the Models page layout.
- Adding new model-management features.
- Adding a separate app animation preference.
- Perpetual decorative animation.
