package me.rerere.rikkahub.ui.pages.setting.components

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.locallm.ModelInstall
import okhttp3.OkHttpClient
import java.io.File

/**
 * Shared download/import plumbing for the local TTS engines (Pocket TTS, Kitten TTS).
 *
 * Files live in `filesDir/local-models/tts-<engine>/`. HF links resolve through the same
 * [ModelInstall.download] pipeline the local-LLM module uses (blob→resolve normalisation,
 * HTML magic-byte sniffing, partial-file resume, magic validation), so a "Download" tap is
 * as robust as the LLM model downloads.
 */
object LocalTtsModelManager {

    const val LOCAL_MODELS_DIRNAME = "local-models"

    fun engineDir(context: Context, engine: String): File =
        File(File(context.filesDir, LOCAL_MODELS_DIRNAME), "tts-$engine").apply { mkdirs() }

    /** Checks that every required file is present in [dir]. Returns missing names. */
    fun missingFiles(dir: File, requiredFiles: List<String>): List<String> =
        requiredFiles.filterNot { File(dir, it).isFile }

    /** Copies a picked folder ([treeUri] from an OpenDocumentTree pick) into the engine dir. */
    suspend fun importBundleFromTree(
        context: Context,
        engine: String,
        treeUri: Uri,
        requiredFiles: List<String>,
    ): File = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("Not a folder: $treeUri")
        val fileByName = mutableMapOf<String, DocumentFile>()
        requiredFiles.forEach { name ->
            root.findFile(name)?.let { fileByName[name] = it }
        }
        val missing = requiredFiles - fileByName.keys
        require(missing.isEmpty()) {
            "Missing files in picked folder: ${missing.joinToString(", ")}"
        }
        val dest = engineDir(context, engine)
        fileByName.forEach { (name, doc) ->
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                File(dest, name).outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Could not read $name from picked folder")
        }
        dest
    }

    /**
     * Downloads every file of a HF bundle into the engine dir, returning the dir.
     * [fileUrls] maps each remote file URL to its local filename; report progress via
     * [onFileDone] (name, index, count) and [onProgress] (percent of current file).
     */
    suspend fun downloadBundle(
        context: Context,
        httpClient: OkHttpClient,
        engine: String,
        fileUrls: List<Pair<String, String>>,
        onFileDone: (name: String, index: Int, count: Int) -> Unit = { _, _, _ -> },
        onProgress: (percent: Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dest = engineDir(context, engine)
        fileUrls.forEachIndexed { index, (url, fileName) ->
            val target = File(dest, fileName)
            ModelInstall.download(httpClient, url, target).collect { p ->
                when (p) {
                    is ModelInstall.Progress.Started -> onProgress(0)
                    is ModelInstall.Progress.Tick -> {
                        val total = p.totalBytes
                        val pct = if (total != null && total > 0)
                            ((p.bytesRead * 100) / total).toInt() else 0
                        onProgress(pct)
                    }
                    is ModelInstall.Progress.Done -> onFileDone(fileName, index, fileUrls.size)
                    is ModelInstall.Progress.Failed -> throw p.cause
                }
            }
        }
        dest
    }
}
