package me.rerere.locallm.litert.image

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Flux2KleinPackageTest {
    @Test
    fun `empty package is not ready`() {
        val result = Flux2KleinPackage(createTempDirectory().toFile()).validate()

        assertEquals(Flux2KleinPackageStatus.NotReady::class, result.status::class)
    }

    @Test
    fun `graphs and host bins without tokenizer are baked prompt ready`() {
        val root = createTempDirectory().toFile()
        createRequiredFiles(root, includeTokenizer = false)

        val result = Flux2KleinPackage(root).validate()

        assertEquals(Flux2KleinPackageStatus.ReadyBakedPrompt, result.status)
        assertTrue(result.missingFiles.contains("qwen_vocab.txt"))
    }

    @Test
    fun `complete package is ready for arbitrary prompts`() {
        val root = createTempDirectory().toFile()
        createRequiredFiles(root, includeTokenizer = true)

        val result = Flux2KleinPackage(root).validate()

        assertEquals(Flux2KleinPackageStatus.Ready, result.status)
        assertTrue(result.missingFiles.isEmpty())
    }

    @Test
    fun `reference installer's flat layout is ready`() {
        val root = createTempDirectory().toFile()
        createRequiredFiles(root, includeTokenizer = true, nestedGraphs = false)

        val result = Flux2KleinPackage(root).validate()

        assertEquals(Flux2KleinPackageStatus.Ready, result.status)
        assertTrue(result.missingFiles.isEmpty())
    }

    @Test
    fun `missing graph identifies the graph`() {
        val root = createTempDirectory().toFile()
        createRequiredFiles(root, includeTokenizer = true)
        File(root, "graphs/kc_double0.tflite").delete()

        val result = Flux2KleinPackage(root).validate()

        assertTrue(result.missingFiles.contains("kc_double0.tflite"))
    }

    @Test
    fun `zero length tokenizer is not ready`() {
        val root = createTempDirectory().toFile()
        createRequiredFiles(root, includeTokenizer = true)
        File(root, "klein_tokenizer/qwen_vocab.txt").writeText("")

        val result = Flux2KleinPackage(root).validate()

        assertEquals(Flux2KleinPackageStatus.ReadyBakedPrompt, result.status)
        assertTrue(result.missingFiles.contains("qwen_vocab.txt"))
    }

    private fun createRequiredFiles(
        root: File,
        includeTokenizer: Boolean,
        nestedGraphs: Boolean = true,
    ) {
        val pkg = Flux2KleinPackage(root)
        root.mkdirs()
        if (nestedGraphs) pkg.graphsDir.mkdirs()
        pkg.binsDir.mkdirs()
        if (nestedGraphs) pkg.tokenizerDir.mkdirs()

        listOf(
            "ke_enc0.tflite", "ke_enc1.tflite", "ke_enc2.tflite", "kc_prep.tflite",
            "kc_double0.tflite", "kc_double1.tflite", "kc_single0.tflite",
            "kc_single1.tflite", "kc_single2.tflite", "kc_single3.tflite",
            "kc_final.tflite", "kv_vae.tflite",
        ).forEach { File(if (nestedGraphs) pkg.graphsDir else root, it).writeText("graph") }

        listOf(
            "inputs_embeds", "enc_mask", "enc_cos", "enc_sin", "cos", "sin", "temb",
            "dsigma", "bn_mean", "bn_std", "unpack_perm", "unpatch_perm", "latents0",
        ).forEach { pkg.bin(it).writeText("bin") }

        if (includeTokenizer) {
            listOf("qwen_vocab.txt", "qwen_merges.txt", "qwen_embed_fp16.bin")
                .forEach { File(if (nestedGraphs) pkg.tokenizerDir else root, it).writeText("tokenizer") }
        }
    }
}
