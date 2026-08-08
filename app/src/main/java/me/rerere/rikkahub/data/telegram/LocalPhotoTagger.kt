package me.rerere.rikkahub.data.telegram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.ai.edge.litert.CompiledModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.locallm.task.NpuTaskInference
import me.rerere.rikkahub.data.datastore.Settings
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opt-in on-device tagging of inbound Telegram photos via the raw LiteRT Interpreter.
 *  Purely additive: returns null (and the caller's text is unchanged) whenever the
 *  toggle is off, no classifier model is installed, or the download/decode fails. */
object LocalPhotoTagger {
    private const val TAG = "LocalPhotoTagger"

    // One confined dispatcher owns any NPU model (scaffolding rule 1); the whole
    // tag() pipeline runs inside it so model use is serialized.
    private val modelDispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1, "ModelDispatcher")

    fun isEnabled(settings: Settings): Boolean = settings.enableTelegramPhotoTagging

    /** Run ImageClassifier on one inbound photo, returning a compact "label(score), " summary. */
    suspend fun tag(settings: Settings, fileId: String, client: TelegramBotClient, context: Context): String? {
        if (!isEnabled(settings)) return null
        val modelFile = installedClassifierFile(context) ?: return null
        val bytes = client.downloadFileBytes(fileId) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return withContext(modelDispatcher) {
            runCatching {
                // NPU first; fall back to the raw LiteRT Interpreter (same runtime as the
                // NPU path via libLiteRt.so, 16KB-aligned) when NPU is unavailable.
                val npu = NpuTaskInference.create(context, modelFile)
                if (npu != null) {
                    try {
                        classifyNpu(npu, bitmap)
                    } finally {
                        npu.close()
                    }
                } else {
                    classifyInterpreter(modelFile, bitmap)
                }
            }.onFailure { Log.w(TAG, "tag failed for $fileId", it) }.getOrNull()
        }
    }

    /** Classify via CompiledModel on NPU. MobileNet/EfficientNet take a 224x224 RGB image. */
    private fun classifyNpu(model: CompiledModel, bitmap: Bitmap): String? {
        val scaled = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val input = model.createInputBuffers()[0]
        val output = model.createOutputBuffers()[0]
        try {
            val pixels = IntArray(224 * 224)
            scaled.getPixels(pixels, 0, 224, 0, 0, 224, 224)
            val floats = FloatArray(pixels.size * 3)
            var i = 0
            for (p in pixels) {
                floats[i++] = ((p shr 16 and 0xFF) / 255f) * 2f - 1f
                floats[i++] = ((p shr 8 and 0xFF) / 255f) * 2f - 1f
                floats[i++] = ((p and 0xFF) / 255f) * 2f - 1f
            }
            // One dummy inference right after create so the first real photo is never the NPU compile.
            input.writeFloat(FloatArray(floats.size))
            model.run(listOf(input), listOf(output))
            input.writeFloat(floats)
            model.run(listOf(input), listOf(output))
            val logits = output.readFloat()
            return logits.indices
                .sortedByDescending { logits[it] }
                .take(3)
                .joinToString(", ") { "class${it}(%.2f)".format(logits[it]) }
                .takeIf { it.isNotBlank() }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            runCatching { input.close() }
            runCatching { output.close() }
        }
    }

    /** Fallback: classify via the raw LiteRT Interpreter (same 224x224 RGB preprocessing as NPU). */
    private fun classifyInterpreter(modelFile: String, bitmap: Bitmap): String? {
        val interpreter = Interpreter(File(modelFile), Interpreter.Options().setNumThreads(2))
        val scaled = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        try {
            val pixels = IntArray(224 * 224)
            scaled.getPixels(pixels, 0, 224, 0, 0, 224, 224)
            val floats = FloatArray(pixels.size * 3)
            var i = 0
            for (p in pixels) {
                floats[i++] = ((p shr 16 and 0xFF) / 255f) * 2f - 1f
                floats[i++] = ((p shr 8 and 0xFF) / 255f) * 2f - 1f
                floats[i++] = ((p and 0xFF) / 255f) * 2f - 1f
            }
            val input = ByteBuffer.allocateDirect(floats.size * 4).order(ByteOrder.nativeOrder())
            input.asFloatBuffer().put(floats)
            val output = ByteBuffer.allocateDirect(interpreter.getOutputTensor(0).numBytes().toInt())
                .order(ByteOrder.nativeOrder())
            interpreter.run(input, output)
            output.rewind()
            val logits = FloatArray(output.capacity() / 4)
            for (j in logits.indices) logits[j] = output.float
            return logits.indices
                .sortedByDescending { logits[it] }
                .take(3)
                .joinToString(", ") { "class${it}(%.2f)".format(logits[it]) }
                .takeIf { it.isNotBlank() }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            runCatching { interpreter.close() }
        }
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
