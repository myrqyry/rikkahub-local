package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class WorkspaceFileSystemTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `import accepts content at the configured limit`() {
        val root = tmp.newFolder("workspace")
        val fileSystem = WorkspaceFileSystem(WorkspaceConfig(maxImportBytes = 4))

        fileSystem.importBytes(root, "import.txt", ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)))

        assertEquals(4L, root.resolve("import.txt").length())
    }

    @Test
    fun `import rejects oversized content and removes partial file`() {
        val root = tmp.newFolder("workspace")
        val fileSystem = WorkspaceFileSystem(WorkspaceConfig(maxImportBytes = 4))
        var rejected = false

        try {
            fileSystem.importBytes(root, "import.txt", ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
        assertFalse(root.resolve("import.txt").exists())
    }
}
