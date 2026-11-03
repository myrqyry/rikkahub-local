package me.rerere.rikkahub.ui.pages.setting.components

import android.content.Context
import android.os.StatFs
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import me.rerere.locallm.ModelInstall
import me.rerere.tts.qwen3.Qwen3TtsCatalogEntry
import me.rerere.tts.qwen3.Qwen3TtsCatalogFile
import okhttp3.OkHttpClient

/** Pinned, hash-verified, resumable installer for Qwen3's legacy LiteRT graphs. */
object Qwen3TtsDownload {
    suspend fun install(
        context: Context,
        client: OkHttpClient,
        entry: Qwen3TtsCatalogEntry,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dir = LocalTtsModelManager.engineDir(context, "qwen3-tts")
        downloadInto(
            dir, client, entry, entry.files,
            availableBytes = StatFs(dir.absolutePath).availableBytes,
            onProgress = onProgress,
        )
    }

    /** Separated from Android storage lookup for deterministic HTTP/FS tests. */
    internal suspend fun downloadInto(
        dir: File,
        client: OkHttpClient,
        entry: Qwen3TtsCatalogEntry,
        files: List<Qwen3TtsCatalogFile>,
        availableBytes: Long,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        require(files.isNotEmpty() && files.map { it.local }.distinct().size == files.size)
        require(files.all {
            it.local.matches(Regex("[A-Za-z0-9_.-]+")) &&
                it.remote.startsWith("legacy/") && !it.remote.contains("..")
        }) { "Invalid pinned Qwen3 manifest" }
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create Qwen3 model directory" }

        val completed = mutableSetOf<String>()
        for (spec in files) {
            val target = File(dir, spec.local)
            if (target.exists()) {
                check(target.isFile && ModelInstall.verifiedArtifactMatches(target, spec.expected)) {
                    "Existing Qwen3 file failed verification: ${spec.local}. " +
                        "It will not be replaced automatically."
                }
                completed += spec.local
            }
        }
        val remainingBytes = files.filterNot { it.local in completed }.sumOf { spec ->
            val partial = File(dir, spec.local + ".partial")
            spec.bytes - if (partial.isFile) partial.length().coerceIn(0, spec.bytes) else 0L
        }
        check(availableBytes > remainingBytes + 512L * 1024L * 1024L) {
            "Not enough free space for Qwen3-TTS: requires another " +
                "${remainingBytes / 1_048_576L} MiB plus 512 MiB reserve"
        }

        val total = files.sumOf { it.bytes }
        var doneBytes = files.filter { it.local in completed }.sumOf { it.bytes }
        onProgress(((doneBytes * 100) / total).toInt())
        for (spec in files) {
            if (spec.local in completed) continue
            val target = File(dir, spec.local)
            var complete = false
            ModelInstall.download(
                client, entry.resolveFileUrl(spec.remote), target,
                expectedArtifact = spec.expected,
            ).collect { event ->
                when (event) {
                    is ModelInstall.Progress.Started -> Unit
                    is ModelInstall.Progress.Tick -> {
                        onProgress(
                            (((doneBytes + event.bytesRead.coerceIn(0L, spec.bytes)) * 100) / total)
                                .toInt().coerceIn(0, 100),
                        )
                    }
                    is ModelInstall.Progress.Failed -> throw event.cause
                    is ModelInstall.Progress.Done -> {
                        check(event.file == target) { "Unexpected downloaded file path" }
                        complete = true
                    }
                }
            }
            check(complete) { "Incomplete download: ${spec.local}" }
            doneBytes += spec.bytes
            onProgress(((doneBytes * 100) / total).toInt())
        }
        check(entry.verifyFiles(dir, files)) { "Qwen3-TTS bundle failed final checksums" }
        dir
    }
}
