package me.rerere.tts.matcha

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MatchaTtsBundleTest {
    @Test
    fun opens_complete_bundle() {
        withBundle { directory ->
            val bundle = MatchaTtsBundle.open(directory)
            assertEquals(directory, bundle.directory)
            assertEquals("ok", bundle.config["status"]?.toString()?.trim('"'))
        }
    }

    @Test
    fun reports_missing_files() {
        withBundle(remove = MatchaTtsBundle.DECODER) { directory ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                MatchaTtsBundle.open(directory)
            }
            assertEquals(true, error.message?.contains(MatchaTtsBundle.DECODER))
        }
    }

    @Test
    fun rejects_invalid_metadata() {
        withBundle { directory ->
            File(directory, MatchaTtsBundle.CONFIG).writeText("not json")
            assertThrows(IllegalArgumentException::class.java) {
                MatchaTtsBundle.open(directory)
            }
        }
    }

    private fun withBundle(remove: String? = null, block: (File) -> Unit) {
        val directory = Files.createTempDirectory("matcha-bundle").toFile()
        try {
            MatchaTtsBundle.requiredFiles.forEach { file ->
                File(directory, file).writeText(
                    when (file) {
                        MatchaTtsBundle.CONFIG -> "{\"status\":\"ok\"}"
                        MatchaTtsBundle.G2P_META -> "{\"tokens\":[] }"
                        else -> "bundle"
                    },
                )
            }
            remove?.let { File(directory, it).delete() }
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
