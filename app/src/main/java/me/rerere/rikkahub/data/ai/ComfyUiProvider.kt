package me.rerere.rikkahub.data.ai

import java.io.IOException
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.EmbeddingGenerationResult
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.GeneratedImagePayload
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Conventional checkpoint txt2img. Not an arbitrary graph interpreter. */
class ComfyUiProvider(private val http: OkHttpClient) : Provider<ProviderSetting.ComfyUi> {
    override suspend fun listModels(providerSetting: ProviderSetting.ComfyUi): List<Model> =
        providerSetting.models

    override suspend fun generateText(
        providerSetting: ProviderSetting.ComfyUi,
        messages: List<UIMessage>, params: TextGenerationParams,
    ): MessageChunk = error("ComfyUI does not generate chat text")

    override suspend fun streamText(
        providerSetting: ProviderSetting.ComfyUi,
        messages: List<UIMessage>, params: TextGenerationParams,
    ): Flow<MessageChunk> = error("ComfyUI does not generate chat text")

    override suspend fun generateEmbedding(
        providerSetting: ProviderSetting.ComfyUi, params: EmbeddingGenerationParams,
    ): EmbeddingGenerationResult = error("ComfyUI does not generate embeddings")

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): Flow<ImageGenerationItem> = flow {
        val config = providerSetting as? ProviderSetting.ComfyUi ?: error("Expected ComfyUI settings")
        check(config.enabled) { "ComfyUI provider is disabled" }
        require(config.models.any { it.id == params.model.id }) { "Model is not configured for ComfyUI" }
        require(params.numOfImages in 1..4) { "ComfyUI supports 1–4 images per request" }
        val api = ComfyUiApi(http, config.baseUrl)
        val graph = standardComfyWorkflow(config, params)
        val clientId = UUID.randomUUID().toString()
        val socket = ComfyUiSocket(http, api.serverUrl, clientId)
        // Connect *before* submitting, so fast executions cannot race the
        // subscription. If websocket fails, the HTTP history path still works.
        var connected = runCatching { socket.connect() }.getOrDefault(false)
        var promptId: String? = null
        ComfyUiProgress.begin(clientId)
        try {
            promptId = api.enqueue(graph, clientId)
            val id = promptId
            val images = withTimeout(10 * 60 * 1000L) {
                var result: List<ComfyOutputImage>? = null
                while (result == null) {
                    val event = if (connected) {
                        withTimeoutOrNull(6500L) { socket.events.receiveCatching().getOrNull() }
                    } else {
                        delay(1500L)
                        null
                    }
                    if (event == ComfyUiSocketEvent.Disconnected) connected = false
                    var terminalSignal = false
                    if (event?.promptId == id) {
                        when (event) {
                            is ComfyUiSocketEvent.Started ->
                                ComfyUiProgress.update(clientId, "Running")
                            is ComfyUiSocketEvent.Node ->
                                ComfyUiProgress.update(
                                    clientId,
                                    if (event.nodeId == null) "Finishing"
                                    else comfyNodeLabel(event.nodeId),
                                )
                            is ComfyUiSocketEvent.Sampling ->
                                ComfyUiProgress.update(
                                    clientId, "Sampling", event.step, event.total,
                                )
                            is ComfyUiSocketEvent.Complete -> {
                                terminalSignal = true
                                ComfyUiProgress.update(clientId, "Saving output")
                            }
                            is ComfyUiSocketEvent.Failed ->
                                error("ComfyUI execution failed: " + event.detail)
                            else -> Unit
                        }
                    }
                    // WebSocket is primary. History is authoritative for
                    // images and recovers from a missing/dropped socket event.
                    if (terminalSignal || event == null || !connected ||
                        event is ComfyUiSocketEvent.Node && event.promptId == id &&
                        event.nodeId == null) {
                        result = api.outputImages(id)
                    }
                }
                result
            }
            check(images.isNotEmpty()) { "ComfyUI finished without images" }
            for (image in images.take(params.numOfImages)) {
                emit(ImageGenerationItem(
                    payload = GeneratedImagePayload.Bytes(
                        bytes = api.fetchImage(image),
                        mimeType = image.mimeType,
                    ),
                ))
            }
        } catch (cancel: CancellationException) {
            // The server-wide /interrupt may stop somebody else's active
            // job. Deleting only our queued work is the safe choice.
            promptId?.let { id ->
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { api.deleteQueued(id) }
                }
            }
            throw cancel
        } finally {
            socket.close()
            ComfyUiProgress.clear(clientId)
        }
    }
}

/** User-readable names for the fixed, known seven-node workflow. */
internal fun comfyNodeLabel(nodeId: String): String = when (nodeId) {
    "1" -> "Loading checkpoint"
    "2", "3" -> "Encoding prompt"
    "4" -> "Preparing latent"
    "5" -> "Sampling"
    "6" -> "Decoding image"
    "7" -> "Saving image"
    else -> "Running node"
}

internal data class ComfyOutputImage(
    val filename: String, val subfolder: String, val type: String,
    val mimeType: String = "image/png",
)

internal class ComfyUiApi(private val http: OkHttpClient, server: String) {
    internal val serverUrl: HttpUrl = requireNotNull(server.trim().trimEnd('/').toHttpUrlOrNull()) {
        "Enter a valid ComfyUI URL (http://host:8188)"
    }.also {
        require(it.username.isEmpty() && it.password.isEmpty() &&
            it.query == null && it.fragment == null) {
            "ComfyUI URL must not embed credentials, a query, or a fragment"
        }
    }
    private val json = Json { ignoreUnknownKeys = true }
    private fun url(path: String): HttpUrl = serverUrl.newBuilder().addPathSegment(path).build()
    private suspend fun request(req: Request): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string()?.take(350).orEmpty()
                throw IOException("ComfyUI HTTP ${response.code}: $detail")
            }
            val body = response.body ?: error("ComfyUI returned no response body")
            val max = if (req.url.encodedPath.endsWith("/view")) 40L * 1024 * 1024 else 2L * 1024 * 1024
            check(body.contentLength() <= max) { "ComfyUI result too large" }
            body.byteStream().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val count = input.read(buf)
                    if (count == -1) break
                    check(out.size().toLong() + count <= max) { "ComfyUI result too large" }
                    out.write(buf, 0, count)
                }
                out.toByteArray()
            }
        }
    }
    private suspend fun getJson(path: String) =
        json.parseToJsonElement(request(Request.Builder().url(url(path)).get().build()).decodeToString())
            .jsonObject

    suspend fun checkpoints(): List<String> {
        val uri = url("models").newBuilder().addPathSegment("checkpoints").build()
        val response = request(Request.Builder().url(uri).get().build())
        return json.parseToJsonElement(response.decodeToString()).jsonArray
            .map { it.jsonPrimitive.content }.sorted()
    }

    suspend fun checkConnection(): String {
        check(getJson("system_stats").containsKey("system")) { "Not a ComfyUI server" }
        return "Connected to ComfyUI"
    }

    suspend fun enqueue(graph: JsonObject, clientId: String): String {
        val body = buildJsonObject { put("prompt", graph); put("client_id", clientId) }
        val data = request(Request.Builder().url(url("prompt"))
            .post(body.toString().toRequestBody("application/json".toMediaType())).build())
        return json.parseToJsonElement(data.decodeToString()).jsonObject["prompt_id"]
            ?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: error("ComfyUI did not acknowledge this workflow")
    }

    /** Null for pending work, list for completed history; errors are surfaced. */
    suspend fun outputImages(promptId: String): List<ComfyOutputImage>? {
        val uri = url("history").newBuilder().addPathSegment(promptId).build()
        val data = request(Request.Builder().url(uri).get().build())
        val entry = json.parseToJsonElement(data.decodeToString()).jsonObject[promptId]
            ?.jsonObject ?: return null
        val status = entry["status"]?.jsonObject
        if (status?.get("status_str")?.jsonPrimitive?.contentOrNull == "error")
            error("ComfyUI workflow failed; inspect server history")
        val outputs = entry["outputs"]?.jsonObject
        if (outputs == null) {
            if (status?.get("completed")?.jsonPrimitive?.contentOrNull == "true")
                error("ComfyUI marked this workflow complete without outputs")
            return null
        }
        return outputs.values.flatMap { value ->
            value.jsonObject["images"]?.jsonArray?.map { image ->
                val item = image.jsonObject
                val type = item["type"]?.jsonPrimitive?.contentOrNull ?: "output"
                require(type in setOf("output", "temp")) { "Invalid output type" }
                val filename = item["filename"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf(String::isNotBlank) ?: error("ComfyUI output filename missing")
                ComfyOutputImage(
                    filename = filename,
                    subfolder = item["subfolder"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    type = type,
                    mimeType = if (filename.endsWith(".jpg", true) || filename.endsWith(".jpeg", true))
                        "image/jpeg" else "image/png",
                )
            }.orEmpty()
        }
    }

    suspend fun fetchImage(image: ComfyOutputImage): ByteArray {
        val uri = url("view").newBuilder()
            .addQueryParameter("filename", image.filename)
            .addQueryParameter("subfolder", image.subfolder)
            .addQueryParameter("type", image.type).build()
        val bytes = request(Request.Builder().url(uri).get().build())
        check(bytes.size > 8) { "ComfyUI returned an empty image" }
        val png = bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        )
        val jpeg = bytes[0] == 0xff.toByte() &&
            bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
        check(png || jpeg) { "ComfyUI did not return PNG/JPEG image data" }
        return bytes
    }

    suspend fun deleteQueued(promptId: String) {
        val data = buildJsonObject {
            put("delete", buildJsonArray { add(JsonPrimitive(promptId)) })
        }
        request(Request.Builder().url(url("queue"))
            .post(data.toString().toRequestBody("application/json".toMediaType())).build())
    }
}

internal fun standardComfyWorkflow(
    config: ProviderSetting.ComfyUi,
    request: ImageGenerationParams,
): JsonObject {
    require(config.checkpoint.isNotBlank()) { "Choose a ComfyUI checkpoint in provider settings" }
    require(config.steps in 1..150 && config.cfg in 0f..30f) { "Invalid sampler parameters" }
    require(config.width in 128..2048 && config.height in 128..2048 &&
        config.width % 8 == 0 && config.height % 8 == 0) { "Dimensions must be multiples of 8" }
    val short = minOf(config.width, config.height)
    val long = maxOf(config.width, config.height, short * 3 / 2).let { it / 8 * 8 }
    val (width, height) = when (request.aspectRatio) {
        ImageAspectRatio.SQUARE -> short to short
        ImageAspectRatio.LANDSCAPE -> long to short
        ImageAspectRatio.PORTRAIT -> short to long
    }
    val seed = if (config.seed >= 0) config.seed else Random.nextLong(0, Long.MAX_VALUE)
    fun link(id: String, port: Int) = buildJsonArray {
        add(JsonPrimitive(id)); add(JsonPrimitive(port))
    }
    fun node(type: String, inputs: JsonObject) = buildJsonObject {
        put("class_type", type); put("inputs", inputs)
    }
    return buildJsonObject {
        put("1", node("CheckpointLoaderSimple", buildJsonObject {
            put("ckpt_name", config.checkpoint)
        }))
        put("2", node("CLIPTextEncode", buildJsonObject {
            put("text", request.prompt); put("clip", link("1", 1))
        }))
        put("3", node("CLIPTextEncode", buildJsonObject {
            put("text", config.negativePrompt); put("clip", link("1", 1))
        }))
        put("4", node("EmptyLatentImage", buildJsonObject {
            put("width", width); put("height", height); put("batch_size", request.numOfImages)
        }))
        put("5", node("KSampler", buildJsonObject {
            put("seed", seed); put("steps", config.steps); put("cfg", config.cfg)
            put("sampler_name", config.sampler); put("scheduler", config.scheduler)
            put("denoise", 1.0)
            put("model", link("1", 0)); put("positive", link("2", 0))
            put("negative", link("3", 0)); put("latent_image", link("4", 0))
        }))
        put("6", node("VAEDecode", buildJsonObject {
            put("samples", link("5", 0)); put("vae", link("1", 2))
        }))
        put("7", node("SaveImage", buildJsonObject {
            put("filename_prefix", "RikkaHub"); put("images", link("6", 0))
        }))
    }
}
