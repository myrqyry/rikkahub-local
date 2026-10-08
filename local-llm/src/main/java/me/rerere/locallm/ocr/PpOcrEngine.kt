package me.rerere.locallm.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.locallm.ocr.compiled.PpocrDetector
import me.rerere.locallm.ocr.compiled.PpocrRecognizer

class PpOcrEngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Real PP-OCRv5 image-to-text pipeline.
 *
 * Uses the GPU-clean PP-OCRv5 converted graphs and decoding from Google's
 * litert-samples Model Zoo (Apache-2.0, source snapshot
 * 83add91158301c911f34c7e0a74c3fa5c2ebc7e1).
 *
 * Previous implementation fed 320x320 NHWC pixels into a 640x640 NCHW
 * DB detector, passed an arbitrary bounding crop to the recognizer, and did
 * not perform CTC collapsing. The outputs were not trustworthy OCR.
 *
 * This implementation retains the model's own channel order, normalization,
 * dimensions, connected component text detection, padded line crops and CTC
 * decoding. Every graph/buffer is closed on both normal and failed runs.
 */
open class PpOcrEngine(
    private val modelDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1, "PpOcr"),
) {
    open suspend fun recognize(imagePath: String, detPath: String, recPath: String): String =
        withContext(modelDispatcher) {
            val detFile = File(detPath)
            if (!detFile.isFile) throw PpOcrEngineException("PP-OCR detection model not found: $detPath")
            val recFile = File(recPath)
            if (!recFile.isFile) throw PpOcrEngineException("PP-OCR recognition model not found: $recPath")
            val folder = recFile.parentFile
                ?: throw PpOcrEngineException("PP-OCR model directory unavailable")
            if (detFile.parentFile?.canonicalPath != folder.canonicalPath ||
                detFile.name != PpocrDetector.MODEL ||
                recFile.name != PpocrRecognizer.MODEL
            ) {
                throw PpOcrEngineException(
                    "PP-OCRv5 needs ppocr_det_fp16.tflite and ppocr_rec_fp16.tflite together in one directory"
                )
            }
            val dict = File(folder, PpocrRecognizer.DICT)
            if (!dict.isFile || dict.length() == 0L) {
                throw PpOcrEngineException("PP-OCRv5 dictionary not found: ${dict.absolutePath}")
            }
            val image = BitmapFactory.decodeFile(imagePath)
                ?: throw PpOcrEngineException("Cannot decode OCR image: $imagePath")
            try {
                try {
                    runBackend(folder, image, Accelerator.GPU)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (gpuFailure: Exception) {
                    Log.w("PpOcrEngine", "GPU OCR unavailable; retrying on CPU", gpuFailure)
                    try {
                        runBackend(folder, image, Accelerator.CPU)
                    } catch (cpuFailure: Exception) {
                        cpuFailure.addSuppressed(gpuFailure)
                        throw PpOcrEngineException(
                            "PP-OCRv5 could not execute on GPU or CPU: ${cpuFailure.message}",
                            cpuFailure,
                        )
                    }
                }
            } finally {
                image.recycle()
            }
        }

    private fun runBackend(modelDir: File, image: Bitmap, accelerator: Accelerator): String {
        val detector = PpocrDetector(modelDir, accelerator)
        try {
            val recognizer = PpocrRecognizer(modelDir, accelerator)
            try {
                val letterboxed = letterbox(image)
                try {
                    val rgb = bitmapRgb(letterboxed)
                    val probability = detector.probMap(rgb)
                    val boxes = PpocrDetector.boxes(probability)
                    val lines = ArrayList<Pair<PpocrDetector.Box, String>>()
                    for (box in boxes) {
                        val text = recognizer.recognize(cropAndPad(letterboxed, box))
                        if (text.isNotBlank()) lines.add(box to text)
                    }
                    // Detector returns approximate top-to-bottom/left-to-right order;
                    // retain separate lines rather than silently concatenating regions.
                    return lines.joinToString("\n") { it.second }
                } finally {
                    letterboxed.recycle()
                }
            } finally {
                recognizer.close()
            }
        } finally {
            detector.close()
        }
    }

    private fun letterbox(image: Bitmap): Bitmap {
        val size = PpocrDetector.SIZE
        val scale = minOf(size.toFloat() / image.width, size.toFloat() / image.height)
        val w = (image.width * scale).toInt().coerceAtLeast(1)
        val h = (image.height * scale).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(image, w, h, true)
        try {
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            Canvas(out).apply {
                drawColor(Color.WHITE)
                drawBitmap(resized, (size - w) / 2f, (size - h) / 2f, null)
            }
            return out
        } finally {
            if (resized !== image) resized.recycle()
        }
    }

    private fun bitmapRgb(bitmap: Bitmap): FloatArray {
        val count = bitmap.width * bitmap.height
        val pixels = IntArray(count)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val rgb = FloatArray(count * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            rgb[i * 3] = ((p ushr 16) and 0xff).toFloat()
            rgb[i * 3 + 1] = ((p ushr 8) and 0xff).toFloat()
            rgb[i * 3 + 2] = (p and 0xff).toFloat()
        }
        return rgb
    }

    private fun cropAndPad(img: Bitmap, box: PpocrDetector.Box): FloatArray {
        val w = box.x1 - box.x0 + 1
        val h = box.y1 - box.y0 + 1
        val crop = Bitmap.createBitmap(img, box.x0, box.y0, w, h)
        try {
            val resizedWidth = minOf(
                (PpocrRecognizer.H.toFloat() * w / h).toInt(),
                PpocrRecognizer.W,
            ).coerceAtLeast(1)
            val resized = Bitmap.createScaledBitmap(crop, resizedWidth, PpocrRecognizer.H, true)
            try {
                val px = IntArray(resizedWidth * PpocrRecognizer.H)
                resized.getPixels(px, 0, resizedWidth, 0, 0, resizedWidth, PpocrRecognizer.H)
                val result = FloatArray(PpocrRecognizer.H * PpocrRecognizer.W * 3)
                for (y in 0 until PpocrRecognizer.H) {
                    for (x in 0 until resizedWidth) {
                        val p = px[y * resizedWidth + x]
                        val index = (y * PpocrRecognizer.W + x) * 3
                        result[index] = ((p ushr 16) and 0xff).toFloat()
                        result[index + 1] = ((p ushr 8) and 0xff).toFloat()
                        result[index + 2] = (p and 0xff).toFloat()
                    }
                }
                return result
            } finally {
                if (resized !== crop) resized.recycle()
            }
        } finally {
            crop.recycle()
        }
    }
}
