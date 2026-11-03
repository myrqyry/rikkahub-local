package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.search.SearchServiceOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchPreferenceTest {
    @Test
    fun searchProviderReorderAccountsForLeadingToggleRow() {
        val first = SearchServiceOptions.BingLocalOptions()
        val second = SearchServiceOptions.TavilyOptions()
        val third = SearchServiceOptions.BraveOptions()
        val services = listOf(first, second, third)

        val reordered = reorderSearchServices(
            services = services,
            fromLazyIndex = 1,
            toLazyIndex = 2,
            leadingItemCount = 1,
        )

        assertEquals(listOf(second, first, third), reordered)
    }

    @Test
    fun changingGlobalWebSearchPreferenceDoesNotModifySearchServicesOrOtherSettings() {
        val original = Settings().copy(enableWebSearch = false, searchServiceSelected = 2)
        val enabled = original.withWebSearchEnabled(true)
        assertTrue(enabled.enableWebSearch)
        assertEquals(original.copy(enableWebSearch = true), enabled)
        assertEquals(original.searchServiceSelected, enabled.searchServiceSelected)
        assertEquals(original.searchServices, enabled.searchServices)

        val disabled = enabled.withWebSearchEnabled(false)
        assertFalse(disabled.enableWebSearch)
        assertEquals(original, disabled)
    }
}
