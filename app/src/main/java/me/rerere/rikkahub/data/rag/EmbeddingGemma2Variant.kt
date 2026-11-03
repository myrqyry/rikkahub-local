package me.rerere.rikkahub.data.rag

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Revision- and checksum-pinned LiteRT-LM embedding bundles.
 *
 * A different bundle is NOT assumed to produce the same embedding space
 * just because they share a 768-dimensional output. Each index stays isolated.
 * The previous 270M model remains byte-for-byte compatible and selectable.
 */
enum class EmbeddingGemma2Variant(
    val modelId: String,
    val repo: String,
    val revision: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val supportsImage: Boolean,
    val supportsAudio: Boolean,
    val spaceId: String,
) {
    TEXT(
        EmbeddingGemma2Model.MODEL_ID,
        EmbeddingGemma2Model.REPO,
        EmbeddingGemma2Model.REVISION,
        EmbeddingGemma2Model.FILE_NAME,
        EmbeddingGemma2Model.SIZE_BYTES,
        EmbeddingGemma2Model.SHA256,
        false,
        false,
        EmbeddingGemma2Model.SPACE_ID,
    ),
    VISION(
        "local:embeddinggemma-2-text-vision-440m",
        "litert-community/embeddinggemma-2-text-vision-440m-litert-lm",
        "e301f74d5551b0c2641bd5cb4652a76239d5c5f8",
        "embeddinggemma-2-text-vision-440m.litertlm",
        387710976L,
        "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0",
        true,
        false,
        "local:embeddinggemma2:e301f74d5551b0c2641bd5cb4652a76239d5c5f8:text-vision:768:search-result-v1",
    ),
    FULL(
        "local:embeddinggemma-2-740m",
        "litert-community/embeddinggemma-2-740m-litert-lm",
        "24d962e906c7d332c6428e71c9676855024569e2",
        "embeddinggemma-2-740m.litertlm",
        484622336L,
        "e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c",
        true,
        true,
        "local:embeddinggemma2:24d962e906c7d332c6428e71c9676855024569e2:full:768:search-result-v1",
    );

    val dimension: Int get() = EmbeddingGemma2Model.DIMENSION
    val downloadUrl: String get() = "https://huggingface.co/$repo/resolve/$revision/$fileName"

    fun file(context: Context): File = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "local-models/embeddinggemma2/$fileName",
    )

    fun present(context: Context): Boolean = file(context).let { it.isFile && it.length() == sizeBytes }

    fun verify(file: File): Boolean {
        if (!file.isFile || file.length() != sizeBytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val length = stream.read(buffer)
                if (length < 0) break
                digest.update(buffer, 0, length)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == sha256
    }

    companion object {
        fun forModelId(id: String): EmbeddingGemma2Variant? = entries.firstOrNull { it.modelId == id }
    }
}
