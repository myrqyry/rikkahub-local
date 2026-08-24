package me.rerere.locallm.litert.image

import java.io.File

sealed interface Flux2KleinPackageStatus {
    data object Ready : Flux2KleinPackageStatus
    data object ReadyBakedPrompt : Flux2KleinPackageStatus
    data class NotReady(val reason: String) : Flux2KleinPackageStatus
}

data class Flux2KleinPackageValidation(
    val status: Flux2KleinPackageStatus,
    val missingFiles: List<String> = emptyList(),
)

class Flux2KleinPackage(
    val root: File,
) {
    val graphsDir: File = File(root, "graphs")
    val binsDir: File = File(root, "klein_bins")
    val tokenizerDir: File = File(root, "klein_tokenizer")

    fun graph(name: String): File = File(graphsDir, name)

    fun bin(name: String): File = File(binsDir, "$name.bin")

    fun tokenizerAsset(name: String): File = File(tokenizerDir, name)

    fun validate(): Flux2KleinPackageValidation = Flux2KleinPackageValidator.validate(this)
}

object Flux2KleinPackageValidator {
    private val graphFiles = listOf(
        "ke_enc0.tflite",
        "ke_enc1.tflite",
        "ke_enc2.tflite",
        "kc_prep.tflite",
        "kc_double0.tflite",
        "kc_double1.tflite",
        "kc_single0.tflite",
        "kc_single1.tflite",
        "kc_single2.tflite",
        "kc_single3.tflite",
        "kc_final.tflite",
        "kv_vae.tflite",
    )

    private val binFiles = listOf(
        "inputs_embeds.bin",
        "enc_mask.bin",
        "enc_cos.bin",
        "enc_sin.bin",
        "cos.bin",
        "sin.bin",
        "temb.bin",
        "dsigma.bin",
        "bn_mean.bin",
        "bn_std.bin",
        "unpack_perm.bin",
        "unpatch_perm.bin",
        "latents0.bin",
    )

    private val tokenizerFiles = listOf(
        "qwen_vocab.txt",
        "qwen_merges.txt",
        "qwen_embed_fp16.bin",
    )

    fun validate(pkg: Flux2KleinPackage): Flux2KleinPackageValidation {
        val missingGraphs = graphFiles.filterNot { isValidFile(pkg.graph(it)) }
        val missingBins = binFiles.filterNot { isValidFile(pkg.bin(it.removeSuffix(".bin"))) }
        val missingRequired = missingGraphs + missingBins
        if (missingRequired.isNotEmpty()) {
            return Flux2KleinPackageValidation(
                status = Flux2KleinPackageStatus.NotReady(
                    "FLUX.2-klein package is missing or invalid: ${missingRequired.joinToString()}",
                ),
                missingFiles = missingRequired,
            )
        }

        val missingTokenizer = tokenizerFiles.filterNot { isValidFile(pkg.tokenizerAsset(it)) }
        return if (missingTokenizer.isEmpty()) {
            Flux2KleinPackageValidation(Flux2KleinPackageStatus.Ready)
        } else {
            Flux2KleinPackageValidation(
                status = Flux2KleinPackageStatus.ReadyBakedPrompt,
                missingFiles = missingTokenizer,
            )
        }
    }

    private fun isValidFile(file: File): Boolean = file.isFile && file.length() > 0L
}
