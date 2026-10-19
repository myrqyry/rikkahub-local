package me.rerere.rikkahub.data.ai.prompts

/** Upgrade ONLY an unchanged old built-in default. Custom user strings survive intact.
 * Null keys adopt the current bundled prompt. This is read-time migration so an
 * interrupted app update never rewrites user preferences just to display settings.
 */
internal fun migrateUtilityPrompt(stored: String?, legacy: String, current: String): String =
    if (stored == null || stored == legacy) current else stored
