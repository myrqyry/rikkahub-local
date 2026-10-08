package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRemovalTest {
    @Test
    fun removingRikkaHubBuiltInTombstonesItsIdAndPreservesOtherSources() {
        val builtin = DEFAULT_PROVIDERS.single { it.name == "RikkaHub" }
        val settings = Settings(providers = DEFAULT_PROVIDERS)
        val after = settings.withoutProvider(builtin)

        assertFalse(after.providers.any { it.id == builtin.id })
        assertTrue(builtin.id in after.deletedBuiltInProviderIds)
        assertEquals(settings.providers.size - 1, after.providers.size)
        assertEquals(
            settings.providers.filterNot { it.id == builtin.id },
            after.providers,
        )
    }

    @Test
    fun removingUserAddedProviderDoesNotTombstoneBuiltInIds() {
        val custom = ProviderSetting.OpenAI(name = "Example private provider", builtIn = false)
        val settings = Settings(providers = listOf(custom))
        val after = settings.withoutProvider(custom)

        assertTrue(after.providers.isEmpty())
        assertTrue(after.deletedBuiltInProviderIds.isEmpty())
    }

    @Test
    fun deletingAbsentProviderDoesNotChangeSettings() {
        val builtin = DEFAULT_PROVIDERS.single { it.name == "RikkaHub" }
        val settings = Settings(providers = DEFAULT_PROVIDERS.filterNot { it.id == builtin.id })
        assertEquals(settings, settings.withoutProvider(builtin))
    }
}
