package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchPreferenceTest {
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
