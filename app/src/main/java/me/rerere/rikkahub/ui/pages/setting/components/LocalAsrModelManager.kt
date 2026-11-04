package me.rerere.rikkahub.ui.pages.setting.components

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.asr.WhisperLiteRTModelEntry
import me.rerere.asr.NemotronGgufModel
import me.rerere.asr.ParakeetMediaPipeModel
import me.rerere.locallm.VerifiedModelArtifact
import me.rerere.locallm.ModelInstall
import okhttp3.OkHttpClient
import java.io.File

object LocalAsrModelManager {
    fun engineDir(context: Context): File =
        File(context.filesDir, "models/whisper-litert").apply { mkdirs() }

    fun nemotronDir(context: Context): File =
        File(context.filesDir, "models/nemotron-gguf").apply { mkdirs() }

    fun parakeetDir(context: Context): File =
        File(context.filesDir, "models/mediapipe-parakeet").apply { mkdirs() }

    suspend fun downloadParakeet(
        context: Context,
        client: OkHttpClient,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val directory = parakeetDir(context)
        val target = File(directory, ParakeetMediaPipeModel.filename)
        val expected = VerifiedModelArtifact(ParakeetMediaPipeModel.sizeBytes, ParakeetMediaPipeModel.sha256)
        // Do not overwrite existing working models or surprise the user with disk exhaustion.
        if (!ModelInstall.verifiedArtifactMatches(target, expected)) {
            val reserve = 256L * 1024 * 1024
            val partial = File(target.absolutePath + ".partial")
            val remaining = (ParakeetMediaPipeModel.sizeBytes - partial.length()).coerceAtLeast(0L)
            require(directory.usableSpace > remaining + reserve) {
                "Not enough free space for Parakeet (~615 MB model plus 256 MB reserve)"
            }
        }
        ModelInstall.download(
            client, ParakeetMediaPipeModel.downloadUrl, target,
            expectedArtifact = expected,
        ).collect { progress ->
            when (progress) {
                is ModelInstall.Progress.Started -> onProgress(0)
                is ModelInstall.Progress.Tick -> {
                    val total = progress.totalBytes
                    onProgress(if (total != null && total > 0) (progress.bytesRead * 100 / total).toInt() else 0)
                }
                is ModelInstall.Progress.Done -> onProgress(100)
                is ModelInstall.Progress.Failed -> throw progress.cause
            }
        }
        target
    }

    suspend fun downloadNemotron(
        context: Context,
        client: OkHttpClient,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val target = File(nemotronDir(context), NemotronGgufModel.filename)
        ModelInstall.download(client, NemotronGgufModel.downloadUrl, target,
            expectedArtifact = VerifiedModelArtifact(NemotronGgufModel.sizeBytes, NemotronGgufModel.sha256),
        ).collect { progress ->
            when (progress) {
                is ModelInstall.Progress.Started -> onProgress(0)
                is ModelInstall.Progress.Tick -> {
                    val total = progress.totalBytes
                    onProgress(if (total != null && total > 0) (progress.bytesRead * 100 / total).toInt() else 0)
                }
                is ModelInstall.Progress.Done -> onProgress(100)
                is ModelInstall.Progress.Failed -> throw progress.cause
            }
        }
        target
    }

    suspend fun downloadModel(
        context: Context,
        client: OkHttpClient,
        entry: WhisperLiteRTModelEntry,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val target = File(engineDir(context), entry.filename)
        ModelInstall.download(client, entry.downloadUrl, target).collect { progress ->
            when (progress) {
                is ModelInstall.Progress.Started -> onProgress(0)
                is ModelInstall.Progress.Tick -> {
                    val total = progress.totalBytes
                    onProgress(
                        if (total != null && total > 0) {
                            (progress.bytesRead * 100 / total).toInt()
                        } else {
                            0
                        },
                    )
                }
                is ModelInstall.Progress.Done -> onProgress(100)
                is ModelInstall.Progress.Failed -> throw progress.cause
            }
        }
        target
    }
}
