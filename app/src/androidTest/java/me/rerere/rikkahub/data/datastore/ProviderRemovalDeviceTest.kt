package me.rerere.rikkahub.data.datastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Read-only device acceptance of provider-deletion semantics.
 * This constructs in-memory Settings; it does NOT update installed user's datastore.
 *
 * Run ONLY by manual adb instrument, NOT Gradle connectedDebugAndroidTest on user data.
 */
@RunWith(AndroidJUnit4::class)
class ProviderRemovalDeviceTest {
    @Test
    fun builtInRikkaHubRemovalPersistsItsTombstone() {
        val target = DEFAULT_PROVIDERS.single { it.name == "RikkaHub" }
        val before = Settings(providers = DEFAULT_PROVIDERS)
        val after = before.withoutProvider(target)
        assertFalse(after.providers.any { it.id == target.id })
        assertTrue(after.deletedBuiltInProviderIds.contains(target.id))
        assertEquals(before.providers.size - 1, after.providers.size)
        assertEquals(before.providers.filterNot { it.id == target.id }, after.providers)
    }

    @Test
    fun customProviderRemovalDoesNotTombstone() {
        val user = ProviderSetting.OpenAI(name = "My personal provider", builtIn = false)
        val after = Settings(providers = listOf(user)).withoutProvider(user)
        assertTrue(after.providers.isEmpty())
        assertTrue(after.deletedBuiltInProviderIds.isEmpty())
    }

    @Test
    fun removingUnknownProviderLeavesOtherSettingsUnchanged() {
        val target = DEFAULT_PROVIDERS.single { it.name == "RikkaHub" }
        val before = Settings(providers = DEFAULT_PROVIDERS.filterNot { it.id == target.id })
        assertEquals(before, before.withoutProvider(target))
    }
}
