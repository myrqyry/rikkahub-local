package me.rerere.rikkahub.data.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import java.io.File
import me.rerere.locallm.ModelInstall
import kotlinx.coroutines.withTimeout
import me.rerere.rikkahub.ui.pages.modelmanager.LiteRtModelsGallery
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Read-only gallery verification: no models or settings are written.
 * Run ONLY via manual adb shell am instrument (not Gradle connectedDebugAndroidTest).
 */
@RunWith(AndroidJUnit4::class)
class LiteRtModelsGalleryDeviceTest {
    @Test
    fun bundledCatalogMatchesUpstreamSourceTypes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val entries = LiteRtModelsGallery.load(context)
        assertEquals(102, entries.size)
        assertEquals(102, entries.map { it.slug }.distinct().size)
        assertEquals(4, entries.count { it.task.startsWith("LLM chat") })
        assertTrue(entries.any { it.name == "PP-OCRv5" })
        assertTrue(entries.any { it.name == "Pocket TTS (Kyutai)" })
        assertTrue(entries.any { it.name == "SmolLM3-3B" })
    }

    @Test
    fun separateModelsDoNotOverwriteEachOther() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val entries = LiteRtModelsGallery.load(context)
        val falcon = entries.single { it.name == "Falcon3-3B-Instruct" }
        val smol = entries.single { it.name == "SmolLM3-3B" }
        val asset = me.rerere.rikkahub.ui.pages.modelmanager.LiteRtGalleryAsset(
            path = "model.litertlm", downloadUrl = "https://huggingface.co/owner/repo/resolve/sha/model.litertlm",
            sizeBytes = null, sha256 = null,
        )
        assertTrue(LiteRtModelsGallery.targetFile(context, falcon, asset).name.endsWith(".litertlm"))
        assertTrue(LiteRtModelsGallery.targetFile(context, falcon, asset) !=
            LiteRtModelsGallery.targetFile(context, smol, asset))
        val tflite = asset.copy(path = "model.tflite")
        val other = LiteRtModelsGallery.targetFile(context, falcon, tflite)
        assertFalse(other.absolutePath.contains("/local-models/litert/gallery-"))
        assertFalse(tflite.isRunnableLiteRtLm)
    }

    @Test
    fun tinyRealTfliteDownloadPassesMagicAndHashWithoutRegistering() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val model = LiteRtModelsGallery.load(context).single { it.name == "CPGA-Net" }
        val asset = withTimeout(45_000L) {
            LiteRtModelsGallery.discover(OkHttpClient(), model)
        }.single { it.path == "cpga_fp16.tflite" }
        assertEquals(93820L, asset.sizeBytes)
        val temp = File(context.cacheDir, "litert-gallery-verify-cpga.tflite")
        // Reserved smoke-test cache filename, never a downloaded user model.
        try {
            assertFalse("Refusing to overwrite a pre-existing cache file", temp.exists())
            val events = withTimeout(45_000L) {
                ModelInstall.download(OkHttpClient(), asset.downloadUrl, temp).toList()
            }
            assertTrue(events.lastOrNull() is ModelInstall.Progress.Done)
            assertTrue("Downloaded bytes must match upstream SHA-256", LiteRtModelsGallery.verify(temp, asset))
            assertFalse(asset.isRunnableLiteRtLm)
        } finally {
            temp.delete()
            File(temp.absolutePath + ".partial").delete()
        }
    }

    @Test
    fun huggingFaceDiscoveryUsesRealPinnedRevisionAndLfsHash() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val model = LiteRtModelsGallery.load(context).single { it.name == "SmolLM3-3B" }
        val assets = withTimeout(45_000L) {
            LiteRtModelsGallery.discover(OkHttpClient(), model)
        }
        val bundle = assets.single { it.path == "model.litertlm" }
        assertTrue(bundle.isRunnableLiteRtLm)
        assertTrue(bundle.downloadUrl.matches(Regex(
            "https://huggingface\\.co/mlboydaisuke/SmolLM3-3B-LiteRT/resolve/[0-9a-f]{40}/model\\.litertlm"
        )))
        assertTrue((bundle.sizeBytes ?: 0L) > 1_000_000_000L)
        assertTrue(bundle.sha256?.matches(Regex("[0-9a-f]{64}")) == true)
    }
}
