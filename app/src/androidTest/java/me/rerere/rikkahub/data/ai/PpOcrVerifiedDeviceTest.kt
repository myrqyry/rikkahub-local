package me.rerere.rikkahub.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.locallm.ocr.PpOcrEngine
import me.rerere.rikkahub.ui.pages.modelmanager.PpOcrVerifiedPackage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MANUAL-ONLY acceptance: requires the 3 vetted published OCR files staged
 * in RikkaHub's external private files. Never run connectedDebugAndroidTest on
 * the live app, and never fetch large assets inside this test.
 */
@RunWith(AndroidJUnit4::class)
class PpOcrVerifiedDeviceTest {
    @Test
    fun checksCompleteModelAndDictionaryAgainstPublisherHashes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (asset in PpOcrVerifiedPackage.files) {
            assertTrue(
                "Missing/corrupted published OCR asset: ${asset.name}",
                PpOcrVerifiedPackage.verify(PpOcrVerifiedPackage.target(context, asset), asset),
            )
        }
    }

    @Test
    fun runsRealImageThroughDetectorAndRecognizer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue("OCR package incomplete", PpOcrVerifiedPackage.verified(context))
        val image = File(context.cacheDir, "ppocr-acceptance-fixture.png")
        val bitmap = Bitmap.createBitmap(900, 260, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 108f
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }
            canvas.drawText("HELLO 123", 38f, 155f, paint)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val recognized = withTimeout(110_000L) {
                PpOcrEngine().recognize(
                    image.absolutePath,
                    PpOcrVerifiedPackage.target(context, PpOcrVerifiedPackage.files[0]).absolutePath,
                    PpOcrVerifiedPackage.target(context, PpOcrVerifiedPackage.files[1]).absolutePath,
                )
            }
            Log.i("PpOcrDeviceAcceptance", "OCR result: $recognized")
            val normalized = recognized.uppercase().replace(Regex("[^A-Z0-9]"), "")
            assertTrue("OCR semantic output mismatch: '$recognized'", "HELLO123" in normalized)
        } finally {
            bitmap.recycle()
            image.delete()
        }
    }
}
