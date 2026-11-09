package me.rerere.rikkahub.ui.pages.assistant

import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class FacetNavigationTest {
    @Test
    fun `settings facets opens the facet list instead of an intermediate menu`() {
        assertEquals(Screen.Assistant, facetSettingsDestination())
    }

    @Test
    fun `built in default facet gets badge even when renamed`() {
        val renamed = Assistant(id = DEFAULT_ASSISTANT_ID, name = "My renamed facet")
        assertTrue(isDefaultFacet(renamed))
    }

    @Test
    fun `empty facet names use neutral fallback so only badge signifies default`() {
        val blank = Assistant(id = Uuid.parse("00000000-0000-0000-0000-000000000124"), name = "")
        assertEquals("Unnamed Facet", facetListDisplayName(blank, "Unnamed Facet"))
        assertEquals("Named Facet", facetListDisplayName(blank.copy(name = "Named Facet"), "Unnamed Facet"))
    }

    @Test
    fun `other facets never receive default badge even when named default`() {
        val namedDefault = Assistant(id = Uuid.parse("00000000-0000-0000-0000-000000000123"), name = "Default Facet")
        assertFalse(isDefaultFacet(namedDefault))
    }
}
