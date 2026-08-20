package me.rerere.rikkahub.ui.pages.imggen

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.tools.image.ResolvedMedia
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialImageReferenceLoaderTest {

    @Test
    fun `artifact-aware studio route preserves its reference`() {
        val route = Screen.ImageGenReference(imageRef = "img_42")

        val encoded = Json.encodeToString(Screen.ImageGenReference.serializer(), route)
        val decoded = Json.decodeFromString(Screen.ImageGenReference.serializer(), encoded)

        assertEquals("img_42", decoded.imageRef)
    }

    @Test
    fun `initial reference is staged once without changing the gallery source`() {
        val root = Files.createTempDirectory("imggen-reference-test").toFile()
        try {
            val sourceDirectory = File(root, "gallery").apply { mkdirs() }
            val stagingDirectory = File(root, "temp").apply { mkdirs() }
            val source = File(sourceDirectory, "original.png").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val media = ResolvedMedia(
                stablePath = source.absolutePath,
                originalReference = "img_42",
                mimeType = "image/png",
                sizeBytes = source.length(),
                temporary = false,
            )
            val loader = InitialImageReferenceLoader(
                targetDirectory = stagingDirectory,
                fileNameFactory = { "staged-reference.png" },
            )

            val first = loader.stage("img_42", media)
            val duplicate = loader.stage("img_42", media)

            assertNotNull(first)
            assertNull(duplicate)
            assertNotEquals(source.absolutePath, first)
            assertArrayEquals(source.readBytes(), File(first!!).readBytes())
            assertEquals(1, stagingDirectory.listFiles()?.size)
            assertTrue(source.exists())

            File(first).delete()
            assertTrue("deleting the staged reference must preserve gallery media", source.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
