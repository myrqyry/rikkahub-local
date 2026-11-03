package me.rerere.rikkahub.data.rag

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Frozen EmbeddingGemma 2 LiteRT-LM text model artifact (Google, Apache-2.0).
 * Hugging Face revision and LFS SHA-256 verified from repository API 2026-10-07.
 * This is an embedding model, NEVER a chat engine.
 *
 * Source model: https://huggingface.co/google/embeddinggemma-2
 * Device bundle: https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm
 */
object EmbeddingGemma2Model {
    const val MODEL_ID = "local:embeddinggemma-2-text-270m"
    const val REPO = "litert-community/embeddinggemma-2-text-270m-litert-lm"
    const val REVISION = "9be6e8b90982095dc05c2bd162e4b954ee4dbac7"
    const val FILE_NAME = "embeddinggemma-2-text-270m.litertlm"
    const val SIZE_BYTES = 164626432L
    const val SHA256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb"
    const val DIMENSION = 768

    // Query and passage prefixes are deliberately different for asymmetric
    // retrieval. Same-prefix (or no-prefix) silently impairs RAG rankings.
    fun searchQuery(text: String) = "task: search result | query: $text"
    fun document(text: String, title: String? = null) =
        "title: ${title?.takeIf { it.isNotBlank() } ?: "none"} | text: $text"

    const val SPACE_ID = "local:embeddinggemma2:$REVISION:text:768:search-result-v1"

    val downloadUrl = "https://huggingface.co/$REPO/resolve/$REVISION/$FILE_NAME"

    fun file(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir,
            "local-models/embeddinggemma2/$FILE_NAME")

    fun present(context: Context): Boolean {
        val f = file(context)
        return f.isFile && f.length() == SIZE_BYTES
    }

    fun verify(file: File): Boolean {
        if (!file.isFile || file.length() != SIZE_BYTES) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(128 * 1024)
            while (true) {
                val bytes = input.read(buf)
                if (bytes < 0) break
                digest.update(buf, 0, bytes)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == SHA256
    }
}
