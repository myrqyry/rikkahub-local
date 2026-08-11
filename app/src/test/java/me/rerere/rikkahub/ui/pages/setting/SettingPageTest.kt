package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val testIcon = ImageVector.Builder(
    name = "test",
    defaultWidth = 1.dp,
    defaultHeight = 1.dp,
    viewportWidth = 1f,
    viewportHeight = 1f,
).build()

class SettingPageTest {

    @Test
    fun `filter keeps matching item and removes empty sections`() {
        val sections = listOf(
            SettingsHomeSection(
                id = "tools",
                title = "Tools",
                items = listOf(
                    SettingsHomeItem(
                        id = "workspaces",
                        title = "Workspaces",
                        description = "Manage projects",
                        icon = testIcon,
                    ),
                ),
            ),
            SettingsHomeSection(
                id = "aiModels",
                title = "AI & Models",
                items = listOf(
                    SettingsHomeItem(
                        id = "localDream",
                        title = "Local Dream",
                        description = "Generate images with an on-device model",
                        icon = testIcon,
                    ),
                ),
            ),
            SettingsHomeSection(
                id = "appearance",
                title = "Appearance",
                items = listOf(
                    SettingsHomeItem(
                        id = "theme",
                        title = "Theme",
                        description = "Customize colors",
                        icon = testIcon,
                    ),
                ),
            ),
        )

        val filtered = filterSettingsSections(sections, "workspace")

        assertEquals(listOf("tools"), filtered.map { it.id })
        assertEquals(listOf("workspaces"), filtered.single().items.map { it.id })
        assertTrue(filtered.single().items.single().matches("project"))

        val localDream = sections[1].items.single()
        assertTrue(localDream.matches("image"))
    }
}
