package me.rerere.rikkahub.skills.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files

class PluginManagerRollbackTest {
    @Test fun `activation files restore backup and remove failed staging`() {
        val root = Files.createTempDirectory("plugin-rollback").toFile()
        try {
            val plugin = root.resolve("plugin")
            val backup = root.resolve("backup")
            val staging = root.resolve("staging")
            plugin.mkdirs()
            backup.mkdirs()
            staging.mkdirs()
            plugin.resolve("plugin.json").writeText("new")
            backup.resolve("plugin.json").writeText("old")
            staging.resolve("partial").writeText("partial")

            restorePluginActivationFiles(plugin, backup, staging)

            assertEquals("old", plugin.resolve("plugin.json").readText())
            assertFalse(backup.exists())
            assertFalse(staging.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
