package me.rerere.rikkahub.data.ai.tools.image

import android.content.Context
import android.net.Uri
import me.rerere.rikkahub.data.ai.tools.ToolInvocationContext
import me.rerere.rikkahub.data.ai.tools.local.ContentUriResolver
import me.rerere.rikkahub.data.ai.tools.local.ContentUriSafetyGuard
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import java.io.File

data class ResolvedMedia(
    val stablePath: String,
    val originalReference: String,
    val mimeType: String,
    val sizeBytes: Long,
    val temporary: Boolean,
)

sealed interface ReferenceKind {
    data object ARTIFACT : ReferenceKind
    data object FILE_URI : ReferenceKind
    data object CONTENT_URI : ReferenceKind
    data object ABSOLUTE_PATH : ReferenceKind
    data object UNKNOWN : ReferenceKind
}

object ReferenceClassifier {
    private val contentUriRegex = Regex("^content://[^\\s]+$")
    private val fileUriRegex = Regex("^file://[^\\s]+$")

    fun classify(reference: String): ReferenceKind = when {
        ImageToolCatalog.galleryIdFrom(reference) != null -> ReferenceKind.ARTIFACT
        fileUriRegex.matches(reference) -> ReferenceKind.FILE_URI
        contentUriRegex.matches(reference) -> ReferenceKind.CONTENT_URI
        reference.startsWith("/") -> ReferenceKind.ABSOLUTE_PATH
        else -> ReferenceKind.UNKNOWN
    }

    fun artifactToGallery(artifactId: String): Int? = ImageToolCatalog.galleryIdFrom(artifactId)

    fun mimeTypeOf(file: File): String = FileUtils.guessMimeType(file, file.name)
}

interface MediaInputResolver {
    suspend fun resolveImage(reference: String, executionContext: ToolInvocationContext): ResolvedMedia
}

class DefaultMediaInputResolver(
    private val context: Context,
    private val filesManager: FilesManager,
    private val genMediaRepository: GenMediaRepository,
) : MediaInputResolver {

    override suspend fun resolveImage(reference: String, executionContext: ToolInvocationContext): ResolvedMedia =
        when (ReferenceClassifier.classify(reference)) {
            ReferenceKind.ARTIFACT -> resolveArtifact(reference)
            ReferenceKind.FILE_URI -> resolveFile(reference.removePrefix("file://"))
            ReferenceKind.CONTENT_URI -> resolveContentUri(reference)
            ReferenceKind.ABSOLUTE_PATH -> resolveFile(reference)
            ReferenceKind.UNKNOWN -> error("invalid_image_ref")
        }

    private suspend fun resolveArtifact(reference: String): ResolvedMedia {
        val galleryId = ReferenceClassifier.artifactToGallery(reference)
            ?: error("invalid_image_ref")
        val entity = genMediaRepository.getById(galleryId) ?: error("image_not_found")
        val file = File(filesManager.getImagesDir(), entity.path.removePrefix("images/"))
        if (!file.exists()) error("image_not_found")
        return ResolvedMedia(
            stablePath = file.absolutePath,
            originalReference = reference,
            mimeType = ReferenceClassifier.mimeTypeOf(file),
            sizeBytes = file.length(),
            temporary = false,
        )
    }

    private fun resolveFile(absolutePath: String): ResolvedMedia {
        val file = File(absolutePath)
        if (!file.exists()) error("image_not_found")
        val mime = ReferenceClassifier.mimeTypeOf(file)
        if (!mime.startsWith("image/")) error("unsupported_image_type")
        return ResolvedMedia(
            stablePath = file.absolutePath,
            originalReference = absolutePath,
            mimeType = mime,
            sizeBytes = file.length(),
            temporary = false,
        )
    }

    private suspend fun resolveContentUri(reference: String): ResolvedMedia {
        // ContentUriSafetyGuard.check returns a Violation (non-null) when structurally invalid.
        if (ContentUriSafetyGuard.check(reference) != null) return error("content_uri_not_granted")
        if (!ContentUriSafetyGuard.isContentUri(reference)) return error("content_uri_not_granted")
        val doc = ContentUriResolver.resolve(context, reference)
            ?: return error("content_uri_not_granted")
        // Stage into a private temp file so the permission does not need to survive
        // cron / resume / background execution.
        val staged = filesManager.stageContentUri(context, Uri.parse(reference), doc)
            ?: return error("content_uri_not_granted")
        val mime = doc.type ?: ReferenceClassifier.mimeTypeOf(staged)
        if (!mime.startsWith("image/")) error("unsupported_image_type")
        return ResolvedMedia(
            stablePath = staged.absolutePath,
            originalReference = reference,
            mimeType = mime,
            sizeBytes = staged.length(),
            temporary = true,
        )
    }
}
