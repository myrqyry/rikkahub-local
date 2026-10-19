package me.rerere.rikkahub.data.ai

import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.COMFYUI_TXT2IMG_MODEL
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.GeneratedImagePayload
import me.rerere.ai.ui.ImageAspectRatio
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ComfyUiProviderTest {
    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9aAe1WYAAAAASUVORK5CYII="
    )
    private fun settings() = ProviderSetting.ComfyUi(
        enabled = true,
        checkpoint = "models/example.safetensors",
        baseUrl = "http://127.0.0.1:8188",
    )
    private fun params() = ImageGenerationParams(
        model = COMFYUI_TXT2IMG_MODEL,
        prompt = "A paper crane under soft lighting",
        numOfImages = 1,
    )
    private fun client(
        reject: Boolean = false,
        requests: MutableList<String> = mutableListOf(),
        sentGraphs: MutableList<String> = mutableListOf(),
    ): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val req = chain.request()
        requests.add(req.method + " " + req.url.encodedPath)
        val buf = Buffer()
        req.body?.writeTo(buf)
        if (req.url.encodedPath == "/prompt") sentGraphs += buf.readUtf8()
        val (code, data) = when (req.url.encodedPath) {
            "/system_stats" -> 200 to """{"system":{"comfyui_version":"test"}}""".toByteArray()
            "/models/checkpoints" -> 200 to """["models/example.safetensors"]""".toByteArray()
            "/prompt" -> if (reject) 400 to """{"error":"Unknown checkpoint"}""".toByteArray()
                else 200 to """{"prompt_id":"job-123","number":1,"node_errors":{}}""".toByteArray()
            "/history/job-123" -> 200 to """{"job-123":{"status":{"completed":true,"status_str":"success"},"outputs":{"7":{"images":[{"filename":"image.png","type":"output","subfolder":""}]}}}}""".toByteArray()
            "/view" -> 200 to png
            else -> 404 to """{"error":"unknown endpoint"}""".toByteArray()
        }
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1)
            .code(code).message("fixture")
            .body(data.toResponseBody()).build()
    }.build()

    @Test fun workflowIsCorrectlyBoundAndOutputUsesExistingImageFlow() = runBlocking {
        val requests = mutableListOf<String>()
        val graphs = mutableListOf<String>()
        val provider = ComfyUiProvider(client(requests = requests, sentGraphs = graphs))
        val output = provider.generateImage(settings(), params()).toList()
        assertEquals(1, output.size)
        val bytes = (output.single().payload as GeneratedImagePayload.Bytes).bytes
        assertArrayEquals(png, bytes)
        assertEquals(listOf("POST /prompt", "GET /history/job-123", "GET /view"), requests)
        val posted = Json.parseToJsonElement(graphs.single()).jsonObject
        assertEquals("A paper crane under soft lighting",
            posted["prompt"]!!.jsonObject["2"]!!.jsonObject["inputs"]!!
                .jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("models/example.safetensors",
            posted["prompt"]!!.jsonObject["1"]!!.jsonObject["inputs"]!!
                .jsonObject["ckpt_name"]!!.jsonPrimitive.content)
        assertEquals("KSampler",
            posted["prompt"]!!.jsonObject["5"]!!.jsonObject["class_type"]!!.jsonPrimitive.content)
    }

    @Test fun readsAvailableCheckpointsUsingDocumentedServerEndpoints() = runBlocking {
        val api = ComfyUiApi(client(), "http://127.0.0.1:8188")
        assertEquals("Connected to ComfyUI", api.checkConnection())
        assertEquals(listOf("models/example.safetensors"), api.checkpoints())
    }

    @Test fun missingCheckpointOrDisabledProviderFailsBeforeNetwork() = runBlocking {
        val req = mutableListOf<String>()
        val provider = ComfyUiProvider(client(requests = req))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                provider.generateImage(settings().copy(checkpoint = ""), params()).toList()
            }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                provider.generateImage(settings().copy(enabled = false), params()).toList()
            }
        }
        assertTrue(req.isEmpty())
    }

    @Test fun serverRejectingWorkflowNeverYieldsFakeImages() = runBlocking {
        val req = mutableListOf<String>()
        val provider = ComfyUiProvider(client(reject = true, requests = req))
        assertThrows(IOException::class.java) {
            runBlocking {
                provider.generateImage(settings(), params()).toList()
            }
        }
        assertEquals(listOf("POST /prompt"), req)
    }

    @Test fun invalidInputNeverBecomesAnExecutableGraph() {
        assertThrows(IllegalArgumentException::class.java) {
            standardComfyWorkflow(settings().copy(width = 511), params())
        }
        assertThrows(IllegalArgumentException::class.java) {
            standardComfyWorkflow(settings().copy(checkpoint = ""), params())
        }
        val graph = standardComfyWorkflow(settings().copy(seed = 42), params().copy(
            aspectRatio = ImageAspectRatio.PORTRAIT,
        ))
        val inputs = graph["4"]!!.jsonObject["inputs"]!!.jsonObject
        assertTrue(inputs["height"]!!.jsonPrimitive.content.toInt() >
            inputs["width"]!!.jsonPrimitive.content.toInt())
        assertEquals("42", graph["5"]!!.jsonObject["inputs"]!!.jsonObject["seed"]!!.jsonPrimitive.content)
    }

    @Test fun urlNeverAllowsCredentialEmbeddingOrQueryParameters() {
        assertThrows(IllegalArgumentException::class.java) {
            ComfyUiApi(client(), "http://u:password@localhost:8188")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ComfyUiApi(client(), "http://localhost:8188?unsafe=true")
        }
    }
}
