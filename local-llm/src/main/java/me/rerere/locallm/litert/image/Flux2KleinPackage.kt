package me.rerere.locallm.litert.image

import android.content.Context
import java.io.File
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Modality
import kotlin.uuid.Uuid

val FLUX2_KLEIN_MODEL = Model(
    modelId = "flux2-klein",
    displayName = "FLUX.2-klein",
    id = Uuid.parse("1d1b7d8b-6bc5-4fc8-8d46-6db6e9ebd8c1"),
    type = ModelType.IMAGE,
    inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
    outputModalities = listOf(Modality.IMAGE),
    format = "litert",
    runtime = "liteRT",
    executionBackend = "litert",
    hardwareAccelerator = "gpu",
)

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
    companion object {
        private const val PACKAGE_PATH = "local-models/flux2-klein"

        fun fromContext(context: Context): Flux2KleinPackage {
            val canonical = canonicalRoot(context)
            runCatching { reconcileLegacyRoot(context, canonical) }
            return Flux2KleinPackage(canonical)
        }

        fun canonicalRoot(context: Context): File = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            PACKAGE_PATH,
        )

        fun legacyRoot(context: Context): File = File(context.filesDir, PACKAGE_PATH)

        /** Promote a valid legacy install without replacing a valid canonical install. */
        internal fun reconcileLegacyRoot(context: Context, canonical: File = canonicalRoot(context)) {
            reconcileRoots(canonical, legacyRoot(context))
        }

        internal fun reconcileRoots(canonical: File, legacy: File) {
            if (legacy.absoluteFile == canonical.absoluteFile || !legacy.exists()) return

            val canonicalStatus = Flux2KleinPackage(canonical).validate().status
            val legacyStatus = Flux2KleinPackage(legacy).validate().status
            val legacyReady = legacyStatus is Flux2KleinPackageStatus.Ready ||
                legacyStatus is Flux2KleinPackageStatus.ReadyBakedPrompt
            if (!legacyReady) return

            val canonicalReady = canonicalStatus is Flux2KleinPackageStatus.Ready ||
                canonicalStatus is Flux2KleinPackageStatus.ReadyBakedPrompt
            if (canonicalReady) {
                legacy.deleteRecursively()
                return
            }

            canonical.parentFile?.mkdirs()
            val backup = File(canonical.parentFile, ".flux2-klein-previous")
            backup.deleteRecursively()
            val hadCanonical = canonical.exists()
            if (hadCanonical) check(canonical.renameTo(backup)) { "Could not back up FLUX package" }
            try {
                check(legacy.renameTo(canonical)) { "Could not promote legacy FLUX package" }
                backup.deleteRecursively()
            } catch (error: Throwable) {
                if (hadCanonical) backup.renameTo(canonical)
                throw error
            }
        }
    }

    val graphsDir: File = File(root, "graphs")
    val binsDir: File = File(root, "klein_bins")
    val tokenizerDir: File = File(root, "klein_tokenizer")

    /** Accept the reference installer's flat graph layout and the app's nested layout. */
    fun graph(name: String): File = File(graphsDir, name).takeIf { it.isFile } ?: File(root, name)

    fun bin(name: String): File = File(binsDir, "$name.bin")

    fun tokenizerAsset(name: String): File =
        File(tokenizerDir, name).takeIf { it.isFile } ?: File(root, name)

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
