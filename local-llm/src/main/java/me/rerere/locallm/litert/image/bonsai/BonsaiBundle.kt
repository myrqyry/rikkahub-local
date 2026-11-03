package me.rerere.locallm.litert.image.bonsai

import android.content.Context
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Modality
import kotlin.uuid.Uuid
import java.io.File
import java.security.MessageDigest

/**
 * Pin every external byte needed for the published 256x256 CPU/XNNPACK pipeline.
 * File size is a cheap availability check; SHA-256 is required before execution.
 *
 * The .tflite files are NOT packaged with the APK or silently downloaded.
 * Source: https://huggingface.co/litert-community/Bonsai-Image-ternary-4B
 */
object BonsaiBundle {
    const val MODEL_ID = "bonsai-image-ternary-4b"
    const val REPO = "litert-community/Bonsai-Image-ternary-4B"
    const val REVISION = "317d577f5f962eb2d5a3980bca2ce047a8338212"
    const val OUTPUT_SIZE = 256

    data class FileSpec(val relative: String, val bytes: Long, val sha256: String) {
        val url: String get() = "https://huggingface.co/$REPO/resolve/$REVISION/$relative"
    }

    val files: List<FileSpec> = listOf(
        FileSpec("textenc_int4.tflite", 1_798_100_240, "12937850e4a1670986e79663649d071f9db4095d9a74ce1cca045551447f7683"),
        FileSpec("dit_256_int4b32.tflite", 2_267_356_304, "4a8f6c12382a9be5ff2d45bb1f70c1cbbce47fe139fa3970b9f868d91565024e"),
        FileSpec("vae_dec_256_fp32.tflite", 198_831_180, "f3ac3b1bca6a48a4fee55779317a3e6adf797336afe82aaa03e8a9b5bbdab324"),
        FileSpec("tokenizer/vocab.json", 2_776_833, "ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910"),
        FileSpec("tokenizer/merges.txt", 1_671_853, "8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5"),
    )
    const val META_SHA256 = "e94faa62e7648db114f3dfcf85fadd5444e64de5e04d8d34ec14e9c306b2a4c6"

    fun fromContext(context: Context): BonsaiBundlePackage = BonsaiBundlePackage(
        File(context.getExternalFilesDir(null) ?: context.filesDir, "local-models/bonsai-image-ternary-4b")
    )

    fun validSha256(file: File, spec: FileSpec): Boolean {
        if (!file.isFile || file.length() != spec.bytes || java.nio.file.Files.isSymbolicLink(file.toPath())) return false
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) } == spec.sha256
    }
}

/** Not presented as installed until the complete bundle has passed size and hash checks. */
val BONSAI_IMAGE_MODEL = Model(
    modelId = BonsaiBundle.MODEL_ID,
    displayName = "Bonsai Image 4B (LiteRT)",
    id = Uuid.parse("20943915-0da4-48ed-9429-7694b4099f7c"),
    type = ModelType.IMAGE,
    inputModalities = listOf(Modality.TEXT),
    outputModalities = listOf(Modality.IMAGE),
    format = "litert",
    runtime = "liteRT",
    executionBackend = "litert",
    hardwareAccelerator = "cpu",
)

/** A distinct installation directory; no file migration or deletion of existing FLUX models. */
class BonsaiBundlePackage(val root: File) {
    fun file(spec: BonsaiBundle.FileSpec): File = File(root, spec.relative)

    fun missingFiles(): List<String> = BonsaiBundle.files.filter { spec ->
        val file = file(spec)
        !file.isFile || java.nio.file.Files.isSymbolicLink(file.toPath()) || file.length() != spec.bytes
    }.map { it.relative }

    private val receipt: File get() = File(root, ".bonsai-verified")
    private val expectedReceipt: String get() =
        BonsaiBundle.REVISION + "\n" +
            BonsaiBundle.files.joinToString("\n") { "${it.relative} ${it.bytes} ${it.sha256}" } + "\n"

    /** Receipt is written only after full SHA checks; cheap enough for UI lifecycle.
     *  Runtime still re-hashes all source files before each generation. */
    fun isInstalled(): Boolean =
        root.isDirectory &&
            !java.nio.file.Files.isSymbolicLink(root.toPath()) &&
            receipt.isFile &&
            !java.nio.file.Files.isSymbolicLink(receipt.toPath()) &&
            receipt.readText() == expectedReceipt &&
            missingFiles().isEmpty()

    fun markVerified() {
        requireVerified()
        check(root.isDirectory && !java.nio.file.Files.isSymbolicLink(root.toPath()))
        receipt.writeText(expectedReceipt)
    }

    /** Scoped removal only for an explicit user removal from Models. */
    fun deleteInstalledFiles() {
        val path = root.toPath().toAbsolutePath().normalize()
        require(path.fileName.toString() == "bonsai-image-ternary-4b" &&
            path.parent?.fileName?.toString() == "local-models") {
            "Refusing to remove files outside the Bonsai model package"
        }
        if (!java.nio.file.Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        require(!java.nio.file.Files.isSymbolicLink(path) &&
            java.nio.file.Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        java.nio.file.Files.walkFileTree(path, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(
                file: java.nio.file.Path,
                attrs: java.nio.file.attribute.BasicFileAttributes,
            ): java.nio.file.FileVisitResult {
                java.nio.file.Files.delete(file)
                return java.nio.file.FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(
                dir: java.nio.file.Path, error: java.io.IOException?,
            ): java.nio.file.FileVisitResult {
                if (error != null) throw error
                java.nio.file.Files.delete(dir)
                return java.nio.file.FileVisitResult.CONTINUE
            }
        })
    }

    fun requireVerified() {
        val missing = missingFiles()
        require(missing.isEmpty()) { "Bonsai installation missing or wrong-size: ${missing.joinToString()}" }
        for (spec in BonsaiBundle.files) {
            require(BonsaiBundle.validSha256(file(spec), spec)) {
                "Bonsai model hash mismatch: ${spec.relative}; do not use this bundle"
            }
        }
    }
}
