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
                "tools",
                "Tools",
                keywords = listOf("tools", "skills"),
                items = listOf(
                    SettingsHomeItem("workspaces", "Workspaces", "Manage projects, folders, and file access", testIcon),
                ),
            ),
            SettingsHomeSection(
                "aiModels",
                "AI & Models",
                keywords = listOf("models", "ai"),
                items = listOf(
                    SettingsHomeItem(
                        "imageStudio",
                        "Image Studio",
                        "Generate images with an on-device model",
                        testIcon,
                        keywords = listOf("image", "generate", "diffusion"),
                    ),
                ),
            ),
            SettingsHomeSection(
                "appearance",
                "Appearance",
                keywords = listOf("theme", "color"),
                items = listOf(
                    SettingsHomeItem("theme", "Theme", "Customize colors", testIcon),
                ),
            ),
        )
        val filtered = filterSettingsSections(sections, "workspace")
        assertEquals(1, filtered.size)
        assertEquals("tools", filtered[0].id)
        assertEquals(1, filtered[0].items.size)
        assertEquals("workspaces", filtered[0].items[0].id)
        assertTrue(filtered[0].items[0].matches("project"))
        assertTrue(sections[1].items[0].matches("image"))
    }

    @Test
    fun `search aliases resolve after regrouping`() {
        val sections = listOf(
            SettingsHomeSection(
                "aiModels",
                "AI & Models",
                keywords = listOf("models", "ai", "provider", "assistant", "prompt"),
                items = listOf(
                    SettingsHomeItem("providers", "Providers", "Configure AI providers", testIcon),
                    SettingsHomeItem("defaultModels", "Default Model and Prompt", "Set default models for each feature", testIcon),
                    SettingsHomeItem("onDeviceModels", "On-device models", "Install and manage on-device models", testIcon),
                    SettingsHomeItem("assistants", "Assistant", "Set up personalized assistants", testIcon),
                    SettingsHomeItem("promptLibrary", "Prompt library", "Manage prompts and quick messages", testIcon),
                ),
            ),
            SettingsHomeSection(
                "experience",
                "Experience",
                keywords = listOf("chat", "voice", "message", "input", "speech"),
                items = listOf(
                    SettingsHomeItem("chatBehavior", "Chat Preferences", "Interaction behavior, scrolling, input settings", testIcon),
                    SettingsHomeItem("chatInterface", "UI Preferences", "Message display, fonts, code blocks", testIcon),
                    SettingsHomeItem(
                        "appearance",
                        "Appearance",
                        "Theme, color mode, dynamic color",
                        testIcon,
                        keywords = listOf("theme", "color mode", "dark mode", "light mode", "dynamic color", "amoled", "palette"),
                    ),
                    SettingsHomeItem("speech", "Speech Services", "Configure text-to-speech and speech recognition providers", testIcon),
                    SettingsHomeItem(
                        "responseNotifications",
                        "Response notifications",
                        "Notifications for completed/generated responses",
                        testIcon,
                        keywords = listOf("conversation", "response", "message alert"),
                    ),
                ),
            ),
            SettingsHomeSection(
                "knowledgeTools",
                "Knowledge & Tools",
                keywords = listOf("knowledge", "search", "rag", "retrieval", "browser", "tools", "skills", "plugins", "mcp", "workspace"),
                items = listOf(
                    SettingsHomeItem("search", "Search Service", "Set up search service", testIcon),
                    SettingsHomeItem("rag", "Knowledge & RAG", "Configure retrieval, embeddings, and vector storage", testIcon),
                    SettingsHomeItem("browser", "Browser", "AI-driven web browser and skill viewer", testIcon),
                    SettingsHomeItem("skills", "Skills", "Manage reusable agent skills and workflows", testIcon),
                    SettingsHomeItem("mcp", "MCP", "Configure MCP Servers", testIcon),
                    SettingsHomeItem("plugins", "Plugins", "Manage installed plugins", testIcon),
                    SettingsHomeItem("workspaces", "Workspaces", "Manage projects, folders, and file access", testIcon),
                ),
            ),
            SettingsHomeSection(
                "automation",
                "Automation & Device",
                keywords = listOf("automation", "workflow", "schedule", "server", "telegram", "device", "accessibility"),
                items = listOf(
                    SettingsHomeItem("webServer", "Web Server", "Expose the app over the network", testIcon),
                    SettingsHomeItem("workflows", "Workflows", "Automated flows", testIcon),
                    SettingsHomeItem("scheduledJobs", "Scheduled jobs", "Run tasks on a schedule", testIcon),
                    SettingsHomeItem("telegram", "Telegram", "Control the app from Telegram", testIcon),
                    SettingsHomeItem(
                        "notificationAccess",
                        "Notification access",
                        "Read device notifications for tools, workflows and routing",
                        testIcon,
                        keywords = listOf("android notification", "channel", "system"),
                    ),
                    SettingsHomeItem("accessibility", "Accessibility", "Device control and automation", testIcon),
                    SettingsHomeItem("termux", "Termux", "Shell commands, timeouts, and Termux integration", testIcon),
                ),
            ),
            SettingsHomeSection(
                "privacySafety",
                "Privacy & Safety",
                keywords = listOf("safety", "privacy", "permission"),
                items = listOf(
                    SettingsHomeItem("permissions", "Permissions", "Control what the app can access", testIcon),
                    SettingsHomeItem("toolApprovals", "Tool Approvals", "Approve tool execution", testIcon),
                ),
            ),
            SettingsHomeSection(
                "dataMaintenance",
                "Data & Maintenance",
                keywords = listOf("data", "storage", "backup", "files", "export", "restore", "developer"),
                items = listOf(
                    SettingsHomeItem("backup", "Backup", "Back up and restore data", testIcon),
                    SettingsHomeItem("chatStorage", "Chat Storage", "Manage stored conversations", testIcon),
                    SettingsHomeItem("requestLogs", "Request Logs", "Inspect network requests", testIcon),
                    SettingsHomeItem("doctor", "Doctor", "Health checks and repairs", testIcon),
                    SettingsHomeItem("about", "About", "About this app", testIcon, keywords = listOf("version", "info", "credits", "help")),
                ),
            ),
        )

        fun filteredIds(query: String): List<String> =
            filterSettingsSections(sections, query).flatMap { it.items }.map { it.id }

        assertEquals(listOf("appearance"), filteredIds("appearance"))
        assertEquals(listOf("responseNotifications"), filteredIds("response"))
        assertEquals(listOf("notificationAccess"), filteredIds("notification access"))
        assertEquals(listOf("about"), filteredIds("about"))
        assertEquals(listOf("doctor"), filteredIds("doctor"))
        assertEquals(listOf("permissions", "toolApprovals"), filteredIds("privacy"))
        assertEquals(listOf("termux"), filteredIds("termux"))
        assertEquals("knowledgeTools", filterSettingsSections(sections, "skills").single().id)
        assertTrue(filterSettingsSections(sections, "zzzzz-no-match").isEmpty())
    }
}
