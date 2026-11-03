package me.rerere.locallm.litert.image.bonsai

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import org.junit.Assert.*
import org.junit.Test

class BonsaiPackageImportTest {
    private val body = "verified tiny fake graph".toByteArray()
    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun spec(name: String = "tokenizer/vocab.json") =
        BonsaiBundle.FileSpec(name, body.size.toLong(), sha(body))

    private fun withPackage(block: (File, File) -> Unit) {
        val scratch = createTempDirectory("bonsai-import").toFile()
        val local = File(scratch, "local-models").apply { mkdirs() }
        val destination = File(local, "bonsai-image-ternary-4b")
        try { block(destination, local) } finally { scratch.deleteRecursively() }
    }

    @Test fun validBundleIsPromotedAfterReceiptCallback() = withPackage { target, local ->
        val stages = mutableListOf<Pair<Long, Long>>()
        val file = spec()
        BonsaiPackageImport.import(
            packageRoot = target,
            specs = listOf(file),
            open = { ByteArrayInputStream(body) },
            onProgress = { copied, total -> stages += copied to total },
            onVerifiedBeforePromote = { staging ->
                assertFalse(target.exists())
                assertEquals(String(body), File(staging, file.relative).readText())
                File(staging, ".receipt-test").writeText("complete")
            },
        )
        assertEquals(String(body), File(target, file.relative).readText())
        assertEquals("complete", File(target, ".receipt-test").readText())
        assertEquals(body.size.toLong() to body.size.toLong(), stages.last())
        assertFalse(File(local, ".bonsai-image-ternary-4b-staging").exists())
    }

    @Test fun wrongDigestCannotInstallAndCleansStaging() = withPackage { target, local ->
        val bad = spec().copy(sha256 = "0".repeat(64))
        assertThrows(IllegalStateException::class.java) {
            BonsaiPackageImport.import(target, listOf(bad), { ByteArrayInputStream(body) })
        }
        assertFalse(target.exists())
        assertFalse(File(local, ".bonsai-image-ternary-4b-staging").exists())
    }

    @Test fun interruptedCopyCannotInstall() = withPackage { target, local ->
        assertThrows(IllegalStateException::class.java) {
            BonsaiPackageImport.import(
                target, listOf(spec()), { ByteArrayInputStream(body) },
                onProgress = { _, _ -> error("cancelled") }
            )
        }
        assertFalse(target.exists())
        assertFalse(File(local, ".bonsai-image-ternary-4b-staging").exists())
    }

    @Test fun failedReceiptGateNeverPromotesStaging() = withPackage { target, _ ->
        assertThrows(IllegalStateException::class.java) {
            BonsaiPackageImport.import(
                target, listOf(spec()), { ByteArrayInputStream(body) },
                onVerifiedBeforePromote = { error("verification failed") },
            )
        }
        assertFalse(target.exists())
    }

    @Test fun existingBundleAndOtherModelsRemainUntouched() = withPackage { target, local ->
        target.mkdirs()
        File(target, "existing.txt").writeText("keep")
        val flux = File(local, "flux2-klein").apply { mkdirs() }
        File(flux, "old.txt").writeText("keep flux")
        assertThrows(IllegalStateException::class.java) {
            BonsaiPackageImport.import(target, listOf(spec()), { ByteArrayInputStream(body) })
        }
        assertEquals("keep", File(target, "existing.txt").readText())
        assertEquals("keep flux", File(flux, "old.txt").readText())
    }

    @Test fun unsafeRelativePathIsRejected() = withPackage { target, _ ->
        assertThrows(IllegalArgumentException::class.java) {
            BonsaiPackageImport.import(
                target,
                listOf(spec("../other.tflite")),
                { ByteArrayInputStream(body) },
            )
        }
        assertFalse(target.exists())
    }

    @Test fun boundedDeletePreservesNeighborDirectories() = withPackage { target, local ->
        target.mkdirs()
        File(target, "tokenizer").mkdirs()
        File(target, "tokenizer/vocab.json").writeText("temporary")
        val other = File(local, "flux2-klein").apply { mkdirs() }
        File(other, "keep.dat").writeText("preserved")
        BonsaiBundlePackage(target).deleteInstalledFiles()
        assertFalse(target.exists())
        assertEquals("preserved", File(other, "keep.dat").readText())
    }
}
