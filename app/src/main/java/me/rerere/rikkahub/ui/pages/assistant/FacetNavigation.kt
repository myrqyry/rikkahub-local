package me.rerere.rikkahub.ui.pages.assistant

import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Assistant

/** Facets in Settings opens the canonical facet list, not an intermediate choice menu. */
internal fun facetSettingsDestination(): Screen = Screen.Assistant

/** The built-in default identity is identified by its stable ID, not a name or active chat selection. */
internal fun isDefaultFacet(assistant: Assistant): Boolean = assistant.id == DEFAULT_ASSISTANT_ID

/** Empty names are not evidence of default status; keep the list label neutral. */
internal fun facetListDisplayName(assistant: Assistant, unnamedLabel: String): String =
    assistant.name.ifBlank { unnamedLabel }
