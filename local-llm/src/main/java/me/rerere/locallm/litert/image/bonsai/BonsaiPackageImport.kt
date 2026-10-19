package me.rerere.locallm.litert.image.bonsai

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.nio.file.Files

/**
 * Explicit SAF-folder import. Never downloads, replaces a prior installation,
 * or copies unlisted files. Validates every byte before promoting the staging directory.
 */
object BonsaiPackageImport {
    fun import(
        packageRoot: File,
        specs: List<BonsaiBundle.FileSpec> = BonsaiBundle.files,
        open: (BonsaiBundle.FileSpec) -> InputStream,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onVerifiedBeforePromote: (File) -> Unit = {},
    ) {
        require(specs.isNotEmpty() && specs.distinctBy { it.relative }.size == specs.size)
        require(specs.all { spec ->
            spec.relative.matches(Regex("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)*")) &&
                !spec.relative.split('/').any { it == "." || it == ".." } &&
                spec.bytes > 0 &&
                spec.sha256.matches(Regex("[0-9a-f]{64}"))
        }) { "Unsafe Bonsai package manifest" }

        val parent = requireNotNull(packageRoot.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Could not create Bonsai model storage" }
        val staging = File(parent, ".bonsai-image-ternary-4b-staging")
        check(!Files.isSymbolicLink(staging.toPath())) { "Staging path is a symlink" }
        check(!packageRoot.exists()) { "Bonsai package exists; import will not overwrite it" }
        if (staging.exists()) check(staging.deleteRecursively()) { "Cannot clear previous Bonsai staging" }
        check(staging.mkdirs()) { "Could not create Bonsai staging" }

        var copied = 0L
        val total = specs.sumOf { it.bytes }
        try {
            for (spec in specs) {
                val output = File(staging, spec.relative)
                check(output.parentFile!!.isDirectory || output.parentFile!!.mkdirs())
                val md = MessageDigest.getInstance("SHA-256")
                var count = 0L
                open(spec).buffered().use { input ->
                    output.outputStream().buffered().use { sink ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            count += read
                            check(count <= spec.bytes) { "Oversized Bonsai file: ${spec.relative}" }
                            sink.write(buffer, 0, read)
                            md.update(buffer, 0, read)
                            copied += read
                            onProgress(copied, total)
                        }
                    }
                }
                check(count == spec.bytes) { "Truncated Bonsai file: ${spec.relative}" }
                val actual = md.digest().joinToString("") { "%02x".format(it) }
                check(actual == spec.sha256) { "SHA-256 mismatch: ${spec.relative}" }
            }
            // The caller writes the complete package receipt inside staging
            // after verifying all files. Promotion is then one atomic rename.
            onVerifiedBeforePromote(staging)
            check(!packageRoot.exists()) { "Bonsai destination appeared during import" }
            check(staging.renameTo(packageRoot)) { "Could not promote verified Bonsai package" }
        } catch (error: Throwable) {
            if (staging.exists()) staging.deleteRecursively()
            throw error
        }
    }
}
