package me.rerere.rikkahub.ui.pages.setting.components

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.asr.WhisperLiteRTModelEntry
import me.rerere.locallm.ModelInstall
import okhttp3.OkHttpClient
import java.io.File

object LocalAsrModelManager {
    fun engineDir(context: Context): File =
        File(context.filesDir, "models/whisper-litert").apply { mkdirs() }

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
