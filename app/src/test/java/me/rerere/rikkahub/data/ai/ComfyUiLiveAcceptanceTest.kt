package me.rerere.rikkahub.data.ai

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import me.rerere.ai.provider.COMFYUI_TXT2IMG_MODEL
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.GeneratedImagePayload
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in LIVE acceptance: exercises the actual provider + real local ComfyUI
 * WebSocket/POST /prompt/history/view and validates decoded image pixels.
 *
 * It WILL generate one real 256px image on the user's GPU and save the result
 * on their ComfyUI server. Never run in CI or ordinary gradle test suites:
 *  RIKKAHUB_COMFY_ACCEPTANCE=1 ./gradlew :app:testDebugUnitTest \
 *    --tests '*ComfyUiLiveAcceptanceTest'
 *
 * No Android app data/device state touched; Pixel UI/galleries are not tested.
 */
class ComfyUiLiveAcceptanceTest {
    @Test fun realLocalCheckpointToImageBytesViaProvider() = runBlocking {
        assumeTrue(
            "Only run by explicit authorization on the user's local ComfyUI host",
            System.getenv("RIKKAHUB_COMFY_ACCEPTANCE") == "1",
        )
        val url = "http://127.0.0.1:8188"
        val http = OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(50))
            .build()

        // Avoid competing with someone else's running Comfy generation.
        val queue = http.newCall(
            Request.Builder().url("$url/queue").get().build(),
        ).execute().use { response ->
            check(response.isSuccessful) { "ComfyUI queue unavailable" }
            response.body.string()
        }
        val queueInfo = kotlinx.serialization.json.Json.parseToJsonElement(queue).jsonObject
        check(queueInfo["queue_running"]?.jsonArray?.isEmpty() == true &&
            queueInfo["queue_pending"]?.jsonArray?.isEmpty() == true) {
            "ComfyUI is busy; live acceptance will not submit another job"
        }

        val api = ComfyUiApi(http, url)
        assertEquals("Connected to ComfyUI", api.checkConnection())
        val installed = api.checkpoints()
        val checkpoint = "dreamshaper_8.safetensors"
        check(checkpoint in installed) { "Known acceptance checkpoint is not installed" }

        val setting = ProviderSetting.ComfyUi(
            enabled = true, baseUrl = url, checkpoint = checkpoint,
            width = 256, height = 256, steps = 8, cfg = 7f,
            seed = 20261020L, sampler = "euler", scheduler = "normal",
            negativePrompt = "text, watermark, blurry",
        )
        val params = ImageGenerationParams(
            model = COMFYUI_TXT2IMG_MODEL,
            prompt = "An illustrated paper crane under warm studio light",
            numOfImages = 1,
        )
        val images = ComfyUiProvider(http).generateImage(setting, params).toList()
        assertEquals(1, images.size)
        val bytes = (images.single().payload as GeneratedImagePayload.Bytes).bytes
        assertTrue("A generated image should contain more than a trivial header", bytes.size > 2048)
        val decoded: BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
            ?: error("Returned bytes do not decode to an image")
        assertEquals(256, decoded.width)
        assertEquals(256, decoded.height)
        // A flat fixture/placeholder cannot satisfy a real generated-image run.
        val samples = HashSet<Int>()
        for (y in 8 until decoded.height step 32) {
            for (x in 8 until decoded.width step 32) samples.add(decoded.getRGB(x, y))
        }
        assertTrue("Generated image has no meaningful pixel variation", samples.size >= 12)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        println("REAL_COMFY_PROVIDER_ACCEPTANCE_PASS width=256 height=256" +
            " bytes=${bytes.size} sampleColors=${samples.size} sha256=$digest")
    }
}
