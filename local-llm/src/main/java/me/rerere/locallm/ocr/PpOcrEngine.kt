package me.rerere.locallm.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.ai.edge.litert.CompiledModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PpOcrEngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

open class PpOcrEngine(
    private val interpreterFactory: InterpreterFactory = TensorFlowInterpreterFactory,
    private val npuDetFactory: (String) -> CompiledModel? = { null },
    private val modelDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1, "ModelDispatcher"),
) {
    fun interface InterpreterFactory {
        fun create(path: String): Interpreter
    }

    object TensorFlowInterpreterFactory : InterpreterFactory {
        override fun create(path: String): Interpreter =
            Interpreter(File(path), Interpreter.Options().setNumThreads(2))
    }

    companion object {
        private const val INPUT_SIZE = 320
        private const val DET_MAP_SIZE = 80
        private const val DET_THRESHOLD = 0.3f
    }

    // ponytail: naive det->rec pipeline. det output is binarized into a single region whose
    // bounding box is cropped and fed to rec; rec logits are argmaxed against a bundled vocab.
    // No polygon reconstruction, no CTC. Upgrade when real OCR accuracy is needed.
    open suspend fun recognize(imagePath: String, detPath: String, recPath: String): String {
        val detFile = File(detPath)
        if (!detFile.exists()) throw PpOcrEngineException("PP-OCR detection model not found: $detPath")
        val recFile = File(recPath)
        if (!recFile.exists()) throw PpOcrEngineException("PP-OCR recognition model not found: $recPath")

        return withContext(modelDispatcher) {
            val detNpu = runCatching { npuDetFactory(detPath) }.getOrNull()
            if (detNpu != null) warmUpDetection(detNpu)
            val rec = interpreterFactory.create(recPath)
            try {
                val image = decodePixels(imagePath) ?: return@withContext ""
                val bbox = if (detNpu != null) {
                    runDetectionNpu(detNpu, image)
                } else {
                    val det = interpreterFactory.create(detPath)
                    try {
                        runDetection(det, image)
                    } finally {
                        det.close()
                    }
                }
                val crop = cropBbox(image, bbox)
                val logits = runRecognition(rec, crop)
                argmaxText(logits, loadVocab(recPath))
            } finally {
                runCatching { detNpu?.close() }
                rec.close()
            }
        }
    }

    private fun warmUpDetection(det: CompiledModel) = runCatching {
        val input = det.createInputBuffers()[0]
        val output = det.createOutputBuffers()[0]
        try {
            input.writeFloat(FloatArray(INPUT_SIZE * INPUT_SIZE * 3))
            det.run(listOf(input), listOf(output))
        } finally {
            runCatching { input.close() }
            runCatching { output.close() }
        }
    }

    private fun runDetectionNpu(det: CompiledModel, image: ByteBuffer): IntArray {
        val input = det.createInputBuffers()[0]
        val output = det.createOutputBuffers()[0]
        try {
            val floats = FloatArray(INPUT_SIZE * INPUT_SIZE * 3)
            val holder = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.nativeOrder())
            image.rewind()
            holder.put(image)
            holder.rewind()
            holder.asFloatBuffer().get(floats)
            input.writeFloat(floats)
            det.run(listOf(input), listOf(output))
            return binarizeBbox(output.readFloat())
        } finally {
            runCatching { input.close() }
            runCatching { output.close() }
        }
    }

    private fun binarizeBbox(detScores: FloatArray): IntArray {
        // 1x80x80x1 region map: binarize at threshold, then take the enclosing box.
        var minX = DET_MAP_SIZE
        var minY = DET_MAP_SIZE
        var maxX = -1
        var maxY = -1
        var idx = 0
        for (y in 0 until DET_MAP_SIZE) {
            for (x in 0 until DET_MAP_SIZE) {
                if (detScores[idx++] > DET_THRESHOLD) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return intArrayOf(0, 0, 0, 0)
        val scale = INPUT_SIZE.toFloat() / DET_MAP_SIZE
        return intArrayOf(
            (minX * scale).toInt(),
            (minY * scale).toInt(),
            ((maxX - minX + 1) * scale).toInt(),
            ((maxY - minY + 1) * scale).toInt(),
        )
    }

    private fun decodePixels(imagePath: String): ByteBuffer? = runCatching {
        val src = BitmapFactory.decodeFile(imagePath) ?: return@runCatching null
        val bitmap = if (src.width == INPUT_SIZE && src.height == INPUT_SIZE) src
        else Bitmap.createScaledBitmap(src, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (p in pixels) {
            buffer.putFloat(((p shr 16 and 0xFF) / 255f) * 2f - 1f)
            buffer.putFloat(((p shr 8 and 0xFF) / 255f) * 2f - 1f)
            buffer.putFloat(((p and 0xFF) / 255f) * 2f - 1f)
        }
        buffer.rewind()
        if (bitmap !== src) bitmap.recycle()
        src.recycle()
        buffer
    }.getOrNull()

    private fun runDetection(det: Interpreter, image: ByteBuffer): IntArray {
        val out = ByteBuffer.allocateDirect(det.getOutputTensor(0).numBytes().toInt())
            .order(ByteOrder.nativeOrder())
        det.run(image, out)
        out.rewind()
        val scores = FloatArray(out.capacity() / 4)
        for (i in scores.indices) scores[i] = out.float
        return binarizeBbox(scores)
    }

    private fun cropBbox(image: ByteBuffer, box: IntArray): ByteBuffer {
        val x = box[0]
        val y = box[1]
        val w = box[2].coerceAtLeast(1)
        val h = box[3].coerceAtLeast(1)
        val crop = ByteBuffer.allocateDirect(w * h * 3 * 4).order(ByteOrder.nativeOrder())
        image.rewind()
        val rowBytes = INPUT_SIZE * 12
        for (row in 0 until h) {
            val src = (y + row).coerceAtMost(INPUT_SIZE - 1)
            image.position(src * rowBytes + x * 12)
            val slice = image.slice()
            slice.limit(w * 12)
            crop.put(slice)
        }
        crop.rewind()
        return crop
    }

    private fun runRecognition(rec: Interpreter, crop: ByteBuffer): FloatArray {
        val outTensor = rec.getOutputTensor(0)
        val numBytes = outTensor.numBytes().toInt()
        val out = ByteBuffer.allocateDirect(numBytes).order(ByteOrder.nativeOrder())
        val inTensor = rec.getInputTensor(0)
        inTensor.shape()?.let { rec.resizeInput(0, it) }
        rec.run(crop, out)
        out.rewind()
        val logits = FloatArray(numBytes / 4)
        for (i in logits.indices) logits[i] = out.float
        return logits
    }

    private fun loadVocab(recPath: String): List<String> {
        val dict = File(File(recPath).parentFile ?: File("."), "ppocrv5_dict.txt")
        return runCatching { dict.readLines().map { it.trim() } }.getOrDefault(emptyList())
    }

    private fun argmaxText(logits: FloatArray, vocab: List<String>): String {
        if (vocab.isEmpty()) return ""
        val stepSize = vocab.size
        val sb = StringBuilder()
        var step = 0
        while (step < logits.size / stepSize) {
            val base = step * stepSize
            var best = 0
            var bestScore = Float.NEGATIVE_INFINITY
            for (i in 0 until stepSize) {
                val v = logits[base + i]
                if (v > bestScore) {
                    bestScore = v
                    best = i
                }
            }
            if (best != 0) {
                val c = vocab.getOrNull(best) ?: continue
                if (c.isNotBlank()) sb.append(c)
            }
            step++
        }
        return sb.toString()
    }
}
