package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting

/**
 * Remove a provider and preserve an explicit user deletion of a shipped built-in.
 *
 * PreferencesStore re-seeds missing defaults on read unless their ID is tombstoned.
 * Removing from the providers list alone only hides a built-in until the next reload.
 *
 * Only use this after a user confirms removing the provider. Does not delete any
 * local model files, and does not alter other provider configurations.
 */
internal fun Settings.withoutProvider(target: ProviderSetting): Settings {
    val storedProvider = providers.firstOrNull { it.id == target.id } ?: return this
    return copy(
        providers = providers.filterNot { it.id == storedProvider.id },
        deletedBuiltInProviderIds =
            if (storedProvider.builtIn) deletedBuiltInProviderIds + storedProvider.id
            else deletedBuiltInProviderIds,
    )
}
