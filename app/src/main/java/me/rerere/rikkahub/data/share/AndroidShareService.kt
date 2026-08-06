package me.rerere.rikkahub.data.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import java.io.File

data class ShareableArtifact(
    val artifactId: String,
    val contentUri: Uri,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long? = null,
)

sealed interface ShareOutcome {
    data class ChooserOpened(val artifactId: String? = null, val mimeType: String? = null) : ShareOutcome
    data class Unsupported(val reason: String) : ShareOutcome
}

/**
 * 解析 PR8 图片产物引用 (img_<galleryId> 或 <galleryId>) 为可共享的 FileProvider content:// URI.
 * 永不暴露 file:// 给其他应用.
 */
class ShareArtifactResolver(
    private val context: Context,
    private val genMediaRepository: GenMediaRepository,
    private val filesManager: FilesManager,
) {
    suspend fun resolve(artifactRef: String): ShareableArtifact? {
        val galleryId = resolveArtifactId(artifactRef) ?: return null
        val entity = runCatching { genMediaRepository.getById(galleryId) }.getOrNull() ?: return null
        val file = File(filesManager.getImagesDir(), entity.path.substringAfterLast('/'))
        if (!file.exists() || !file.canRead()) return null
        val mime = FileUtils.guessMimeType(file, file.name)
        if (mime == "application/octet-stream") return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return ShareableArtifact(
            artifactId = "img_$galleryId",
            contentUri = uri,
            mimeType = mime,
            displayName = file.name,
            sizeBytes = file.length(),
        )
    }

    companion object {
        fun resolveArtifactId(ref: String): Int? {
            val trimmed = ref.trim()
            val idStr = trimmed.removePrefix("img_")
            return idStr.toIntOrNull()?.takeIf { it > 0 }
        }
    }
}

class AndroidShareService(
    private val context: Context,
    private val resolver: ShareArtifactResolver,
) {
    suspend fun resolve(artifactRef: String): ShareableArtifact? = resolver.resolve(artifactRef)

    fun shareText(url: String?, text: String?, subject: String?): ShareOutcome {
        val combined = listOfNotNull(text, url).joinToString("\n")
        if (combined.isBlank()) return ShareOutcome.Unsupported("nothing to share")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, combined)
            if (!subject.isNullOrEmpty()) {
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ShareOutcome.ChooserOpened()
    }

    fun shareArtifact(artifact: ShareableArtifact, text: String?, subject: String?): ShareOutcome {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = artifact.mimeType
            putExtra(Intent.EXTRA_STREAM, artifact.contentUri)
            if (!text.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, text)
            }
            if (!subject.isNullOrEmpty()) {
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ShareOutcome.ChooserOpened(artifactId = artifact.artifactId, mimeType = artifact.mimeType)
    }
}
