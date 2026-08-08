package me.rerere.rikkahub.data.telegram

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import me.rerere.rikkahub.data.datastore.Settings
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.File

/** Opt-in on-device tagging of inbound Telegram photos via TFLite Task Library.
 *  Purely additive: returns null (and the caller's text is unchanged) whenever the
 *  toggle is off, no classifier model is installed, or the download/decode fails. */
object LocalPhotoTagger {
    private const val TAG = "LocalPhotoTagger"

    fun isEnabled(settings: Settings): Boolean = settings.enableTelegramPhotoTagging

    /** Run ImageClassifier on one inbound photo, returning a compact "label(score), " summary. */
    suspend fun tag(settings: Settings, fileId: String, client: TelegramBotClient, context: Context): String? {
        if (!isEnabled(settings)) return null
        val modelFile = installedClassifierFile(context) ?: return null
        val bytes = client.downloadFileBytes(fileId) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val image = TensorImage.fromBitmap(bitmap)
        return runCatching {
            val classifier = ImageClassifier.createFromFile(File(modelFile))
            try {
                classifier.classify(image).firstOrNull()
                    ?.categories
                    ?.take(3)
                    ?.joinToString(", ") { "${it.label}(%.2f)".format(it.score) }
                    ?.takeIf { it.isNotBlank() }
            } finally {
                classifier.close()
            }
        }.onFailure { Log.w(TAG, "tag failed for $fileId", it) }.getOrNull()
    }

    /** Prefer a locally-imported classifier in the LiteRT models dir; null when absent. */
    private fun installedClassifierFile(context: Context): String? {
        val dir = File(context.filesDir, "local-models/litert")
        if (!dir.isDirectory) return null
        return dir.listFiles()
            ?.filter { it.isFile && (it.name == "mobilenet_v2.tflite" || it.name == "efficientnet_b0.tflite") }
            ?.sortedBy { it.name }
            ?.firstOrNull()
            ?.absolutePath
    }
}
