package me.rerere.rikkahub.ui.pages.imggen

import java.io.File
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.ai.tools.image.ResolvedMedia

/**
 * Stages a durable media artifact into ImageGen's disposable reference area.
 * A route reference is handled at most once by this loader, so recomposition or
 * configuration recreation cannot create duplicate reference files.
 */
internal class InitialImageReferenceLoader(
    private val targetDirectory: File,
    private val fileNameFactory: () -> String = { "imggen_ref_${Uuid.random()}.png" },
) {
    private var stagedReference: String? = null

    fun stage(reference: String, media: ResolvedMedia): String? {
        if (stagedReference == reference) return null

        val source = File(media.stablePath)
        if (!source.exists()) error("image_not_found")
        targetDirectory.mkdirs()
        val target = File(targetDirectory, File(fileNameFactory()).name)
        source.copyTo(target, overwrite = false)
        stagedReference = reference
        return target.absolutePath
    }
}
