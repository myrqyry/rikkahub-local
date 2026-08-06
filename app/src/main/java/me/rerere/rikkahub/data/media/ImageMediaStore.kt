package me.rerere.rikkahub.data.media

import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.rikkahub.data.ai.tools.image.ImageOperation
import me.rerere.rikkahub.data.ai.tools.image.ImageToolCatalog
import me.rerere.rikkahub.data.ai.tools.image.StoredImageArtifact
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.repository.GenMediaRepository
import android.graphics.BitmapFactory
import java.io.File

data class MediaArtifactRef(
    val artifactId: String,
    val path: String,
)

interface ImageMediaStore {
    suspend fun saveGenerated(
        item: ImageGenerationItem,
        prompt: String,
        model: ModelDescriptor,
        operation: ImageOperation,
        sourceArtifacts: List<MediaArtifactRef>,
    ): StoredImageArtifact
}

class DefaultImageMediaStore(
    private val filesManager: FilesManager,
    private val genMediaRepository: GenMediaRepository,
) : ImageMediaStore {

    override suspend fun saveGenerated(
        item: ImageGenerationItem,
        prompt: String,
        model: ModelDescriptor,
        operation: ImageOperation,
        sourceArtifacts: List<MediaArtifactRef>,
    ): StoredImageArtifact {
        val timestamp = System.currentTimeMillis()
        val type = when (operation) {
            ImageOperation.IMAGE_GENERATION -> GenMediaEntity.TYPE_IMAGE_GENERATION
            ImageOperation.IMAGE_EDIT -> GenMediaEntity.TYPE_IMAGE_EDIT
            else -> GenMediaEntity.TYPE_IMAGE_GENERATION
        }
        val relativePath = buildImageRelativePath(timestamp, model.displayName)
        val entity = buildEntity(
            relativePath = relativePath,
            modelName = model.displayName,
            prompt = prompt,
            timestamp = timestamp,
            type = type,
            sourceArtifacts = sourceArtifacts.map { it.artifactId },
        )
        val file = filesManager.createImageFileFromBase64(
            item.data,
            File(filesManager.getImagesDir(), File(relativePath).name).absolutePath,
        )
        if (!file.exists()) error("artifact_save_failed")
        val insertedId = genMediaRepository.insertMedia(entity).toInt()
        if (insertedId <= 0) error("artifact_save_failed")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return StoredImageArtifact(
            artifactId = ImageToolCatalog.artifactIdFor(insertedId),
            path = file.absolutePath,
            uri = "file://${file.absolutePath}",
            galleryId = insertedId,
            mimeType = item.mimeType,
            width = bounds.outWidth,
            height = bounds.outHeight,
        )
    }

    companion object {
        fun buildEntity(
            relativePath: String,
            modelName: String,
            prompt: String,
            timestamp: Long,
            type: String,
            sourceArtifacts: List<String>,
        ): GenMediaEntity = GenMediaEntity(
            path = relativePath,
            modelId = modelName,
            prompt = prompt,
            createAt = timestamp,
            type = type,
            sourcePaths = sourceArtifacts.joinToString("\n").ifBlank { null },
        )
    }
}

/**
 * Builds the on-disk relative path for a generated image.
 * [modelName] is provider-controlled, so it is sanitized to [A-Za-z0-9_.-] before
 * being embedded in the path (keeps the DB `relativePath` and the resolved file
 * consistent). `System.nanoTime()` disambiguates files produced within the same
 * millisecond (multi-image generation), so concurrent saves never share a path.
 */
internal fun buildImageRelativePath(timestamp: Long, modelName: String): String {
    val sanitizedModelName = modelName.replace(Regex("[^A-Za-z0-9_.\\-]"), "_").ifBlank { "image" }
    return "images/${timestamp}_${sanitizedModelName}_${System.nanoTime()}.png"
}
