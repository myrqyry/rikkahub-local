package me.rerere.rikkahub.ui.pages.modelmanager

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * PP-OCRv5 manifest from the official Google LiteRT Model Zoo, commit
 * 83add91158301c911f34c7e0a74c3fa5c2ebc7e1, assets/models.json.
 * We pin the HF revision AND verify each complete file's SHA-256.
 *
 * Source: https://github.com/google-ai-edge/litert-samples/tree/main/samples/litert_model_zoo
 * Model: https://huggingface.co/litert-community/PP-OCRv5-LiteRT (Apache-2.0)
 */
object PpOcrVerifiedPackage {
    const val MODEL_ID = "ppocrv5-local-litert"
    const val SOURCE_COMMIT = "83add91158301c911f34c7e0a74c3fa5c2ebc7e1"
    private const val BASE = "https://huggingface.co/litert-community/PP-OCRv5-LiteRT/resolve/cc63dcd9fa8d9a8e29906c8973ff39670d7fb5a0/"
    data class Asset(val name: String, val bytes: Long, val sha256: String) {
        val url: String get() = BASE + name
    }
    val files = listOf(
        Asset("ppocr_det_fp16.tflite", 10094464, "b635b1d7f0e171a19beda7e8f386a62d4f0a3a1c46ed42a09603a902f2059ccc"),
        Asset("ppocr_rec_fp16.tflite", 17173552, "ef7bb5aba20a1717101f0f112dd8cb1ed8b043ebaf96af05a395c6905ca66456"),
        Asset("ppocrv5_dict.txt", 74012, "d1979e9f794c464c0d2e0b70a7fe14dd978e9dc644c0e71f14158cdf8342af1b"),
    )
    val totalBytes: Long get() = files.sumOf { it.bytes }

    fun directory(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "local-models/litert-ocr/ppocrv5")

    fun target(context: Context, asset: Asset): File {
        require(asset in files) { "Unrecognized PP-OCR model asset" }
        return File(directory(context), asset.name)
    }

    /** Lightweight UI status; callers must use verified() before activating inference. */
    fun present(context: Context): Boolean =
        files.all { asset ->
            val f = target(context, asset)
            f.isFile && f.length() == asset.bytes
        }

    fun verified(context: Context): Boolean =
        files.all { asset -> verify(target(context, asset), asset) }

    fun verify(file: File, asset: Asset): Boolean {
        if (asset !in files || !file.isFile || file.length() != asset.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buf = ByteArray(128 * 1024)
            while (true) {
                val count = stream.read(buf)
                if (count < 0) break
                digest.update(buf, 0, count)
            }
        }
        val calculated = digest.digest().joinToString("") { "%02x".format(it) }
        return calculated.equals(asset.sha256, ignoreCase = true)
    }
}
