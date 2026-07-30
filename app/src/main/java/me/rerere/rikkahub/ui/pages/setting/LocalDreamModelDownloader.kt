package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.locallm.ModelInstall
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

object LocalDreamModelDownloader {

    private const val HF_TREE = "https://huggingface.co/api/models/xororz/sd-qnn/tree/main"
    private const val HF_RESOLVE = "https://huggingface.co/xororz/sd-qnn/resolve/main"

    data class RemoteModel(
        val fileName: String,
        val sizeBytes: Long,
    ) {
        val modelName: String get() = fileName
            .removeSuffix(".zip")
            .substringBefore("_qnn2.28")

        val tier: String get() {
            val base = fileName.removeSuffix(".zip")
            return when {
                base.endsWith("_8gen1") -> "Gen 1"
                base.endsWith("_8gen2") -> "Gen 2+"
                base.endsWith("_min") -> "Min"
                else -> ""
            }
        }

        val downloadUrl: String get() = "$HF_RESOLVE/$fileName"
    }

    sealed class Progress {
        data class Started(val totalBytes: Long?) : Progress()
        data class Downloading(val percent: Int) : Progress()
        data class Extracting(val entry: String) : Progress()
        data class Done(val modelDir: File) : Progress()
        data class Failed(val error: String) : Progress()
    }

    @Serializable
    private data class HfEntry(val path: String, val size: Long, val type: String)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchModels(client: OkHttpClient): List<RemoteModel> {
        val req = Request.Builder().url(HF_TREE).build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: return emptyList()
        val entries = json.decodeFromString<List<HfEntry>>(body)
        return entries
            .filter { it.type == "file" && it.path.endsWith(".zip") }
            .map { RemoteModel(it.path, it.size) }
            .sortedBy { it.modelName }
    }

    suspend fun isModelDownloaded(context: Context, model: RemoteModel): Boolean {
        val dir = File(context.filesDir, "models/${model.modelName}")
        return dir.exists() && dir.listFiles()?.isNotEmpty() == true
    }

    suspend fun downloadModel(context: Context, client: OkHttpClient, model: RemoteModel): Flow<Progress> = flow {
        val targetDir = File(context.filesDir, "models/${model.modelName}")
        val zipFile = File(context.cacheDir, model.fileName)

        ModelInstall.download(client, model.downloadUrl, zipFile).collect { p ->
            when (p) {
                is ModelInstall.Progress.Started -> emit(Progress.Started(p.totalBytes))
                is ModelInstall.Progress.Tick -> {
                    val total = p.totalBytes
                    val pct = if (total != null && total > 0)
                        ((p.bytesRead * 100) / total).toInt() else 0
                    emit(Progress.Downloading(pct))
                }
                is ModelInstall.Progress.Done -> {}
                is ModelInstall.Progress.Failed -> emit(Progress.Failed(p.cause.message ?: "Download failed"))
            }
        }

        targetDir.mkdirs()
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val f = File(targetDir, entry.name)
                    f.parentFile?.mkdirs()
                    f.outputStream().use { zis.copyTo(it) }
                }
                entry = zis.nextEntry
            }
        }
        zipFile.delete()
        emit(Progress.Done(targetDir))
    }.flowOn(Dispatchers.IO)
}
