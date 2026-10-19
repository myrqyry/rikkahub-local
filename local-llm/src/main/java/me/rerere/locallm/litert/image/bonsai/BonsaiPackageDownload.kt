package me.rerere.locallm.litert.image.bonsai

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import me.rerere.locallm.ModelInstall
import me.rerere.locallm.VerifiedModelArtifact
import okhttp3.OkHttpClient

/**
 * Download the upstream pinned, five-file package using RikkaHub's existing
 * SHA-256 enforcing and Range-resumable model downloader.
 *
 * A user-confirmed transfer only. Partial files stay in our own staging
 * directory for retry; only a fully verified bundle can be promoted.
 */
object BonsaiPackageDownload {
    suspend fun download(
        client: OkHttpClient,
        packageRoot: File,
        specs: List<BonsaiBundle.FileSpec> = BonsaiBundle.files,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onVerifiedBeforePromote: (File) -> Unit = { BonsaiBundlePackage(it).markVerified() },
    ) {
        require(specs.isNotEmpty() && specs.distinctBy { it.relative }.size == specs.size)
        require(specs.all { spec ->
            spec.relative.matches(Regex("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)*")) &&
                spec.relative.split('/').none { it == "." || it == ".." } &&
                spec.bytes > 0 && spec.sha256.matches(Regex("[0-9a-f]{64}"))
        }) { "Invalid pinned Bonsai manifest" }

        val parent = requireNotNull(packageRoot.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create model storage" }
        val staging = File(parent, ".bonsai-image-ternary-4b-staging")
        check(!packageRoot.exists()) { "Bonsai is already installed; will not overwrite it" }
        check(!Files.isSymbolicLink(staging.toPath())) { "Unsafe staging directory" }
        check(staging.isDirectory || staging.mkdirs()) { "Cannot create download staging directory" }

        val total = specs.sumOf { it.bytes }
        var completed = 0L
        onProgress(0L, total)

        for (spec in specs) {
            currentCoroutineContext().ensureActive()
            val target = File(staging, spec.relative)
            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) {
                "Cannot create Bonsai tokenizer or graph folder"
            }
            check(!Files.isSymbolicLink(target.toPath())) { "Unsafe staging file" }

            if (target.exists()) {
                if (BonsaiBundle.validSha256(target, spec)) {
                    completed += spec.bytes
                    onProgress(completed, total)
                    continue
                }
                // This is downloader-owned staging, not a user model or an installed
                // file. Redownload corrupt completed files; never overwrite a model.
                check(target.delete()) { "Cannot remove invalid staged file: " + spec.relative }
            }

            var done = false
            var failure: Throwable? = null
            ModelInstall.download(
                client = client,
                url = spec.url,
                target = target,
                expectedArtifact = VerifiedModelArtifact(spec.bytes, spec.sha256),
            ).collect { event ->
                when (event) {
                    is ModelInstall.Progress.Started -> Unit
                    is ModelInstall.Progress.Tick -> onProgress(
                        completed + event.bytesRead.coerceIn(0L, spec.bytes), total
                    )
                    is ModelInstall.Progress.Failed -> failure = event.cause
                    is ModelInstall.Progress.Done -> {
                        check(event.file == target)
                        done = true
                    }
                }
            }
            failure?.let { throw IllegalStateException(
                "Failed downloading " + spec.relative + ": " + it.message, it
            ) }
            check(done) { "Incomplete Bonsai file: " + spec.relative }
            completed += spec.bytes
            onProgress(completed, total)
        }

        currentCoroutineContext().ensureActive()
        // The receipt is written after a full verification INSIDE staging, so
        // a failed copy/download can never become a visible installed package.
        onVerifiedBeforePromote(staging)
        currentCoroutineContext().ensureActive()
        check(!packageRoot.exists()) { "Bonsai was installed by another operation" }
        check(staging.renameTo(packageRoot)) { "Could not promote verified Bonsai model" }
    }

    /** Exact bytes required for an interrupted download, plus a caller-chosen
     * safety reserve. Existing valid completed artifacts and partials count. */
    fun remainingBytes(
        packageRoot: File,
        specs: List<BonsaiBundle.FileSpec> = BonsaiBundle.files,
    ): Long {
        val staging = File(requireNotNull(packageRoot.parentFile), ".bonsai-image-ternary-4b-staging")
        if (Files.isSymbolicLink(staging.toPath())) error("Unsafe Bonsai download staging path")
        return specs.sumOf { spec ->
            val target = File(staging, spec.relative)
            val partial = File(target.absolutePath + ".partial")
            if (target.isFile && BonsaiBundle.validSha256(target, spec)) 0L
            else if (partial.isFile && !Files.isSymbolicLink(partial.toPath()))
                (spec.bytes - partial.length().coerceIn(0L, spec.bytes))
            else spec.bytes
        }
    }
}
