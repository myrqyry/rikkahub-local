package me.rerere.rikkahub.data.opencode

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

internal class OpenCodeHttpClient(
    private val client: OkHttpClient,
    baseUrl: String,
    private val credential: String?,
) : OpenCodeClient {
    private val baseUrl = baseUrl.trimEnd('/')
    private val jsonMediaType = "application/json".toMediaType()

    override suspend fun health(): OpenCodeHealth = get("/global/health").jsonObject.let { body ->
        OpenCodeHealth(
            healthy = body.boolean("healthy") ?: true,
            version = body.string("version"),
        )
    }

    override suspend fun listProjects(): List<OpenCodeProject> = get("/project").arrayItems().mapNotNull { item ->
        val directory = item.string("worktree") ?: item.string("directory") ?: return@mapNotNull null
        OpenCodeProject(item.string("id"), directory, item.string("name"))
    }

    override suspend fun createSession(directory: String?): OpenCodeSession =
        post("/session", directoryQuery(directory), buildJsonObject {}).jsonObject.toSession()

    override suspend fun listSessions(directory: String?): List<OpenCodeSession> =
        get("/session", directoryQuery(directory)).arrayItems().map { it.toSession() }

    override suspend fun getSession(sessionId: String, directory: String?): OpenCodeSession =
        get("/session/${sessionId.pathSegment()}", directoryQuery(directory)).jsonObject.toSession()

    override suspend fun getMessages(sessionId: String, directory: String?): List<OpenCodeMessage> =
        get("/session/${sessionId.pathSegment()}/message", directoryQuery(directory))
            .arrayItems().mapNotNull { it.toMessageOrNull() }

    override suspend fun prompt(sessionId: String, directory: String?, text: String): OpenCodeMessage =
        post(
            "/session/${sessionId.pathSegment()}/message",
            directoryQuery(directory),
            buildJsonObject { put("parts", kotlinx.serialization.json.buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) }) },
        ).jsonObject.toMessageOrNull() ?: error("OpenCode prompt returned no assistant message")

    override suspend fun abortSession(sessionId: String, directory: String?): Boolean {
        request("POST", "/session/${sessionId.pathSegment()}/abort", directoryQuery(directory), null)
        return true
    }

    private suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement =
        request("GET", path, query, null)

    private suspend fun post(path: String, query: Map<String, String>, body: JsonObject): JsonElement =
        request("POST", path, query, body)

    private suspend fun request(
        method: String,
        path: String,
        query: Map<String, String>,
        body: JsonObject?,
    ): JsonElement = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path".toHttpUrl().newBuilder()
        query.forEach { (key, value) -> url.addQueryParameter(key, value) }
        val request = Request.Builder().url(url.build()).method(
            method,
            body?.toString()?.toRequestBody(jsonMediaType),
        ).apply {
            credential?.let { value ->
                header("Authorization", if (value.startsWith("Basic ") || value.startsWith("Bearer ")) value else "Basic ${Base64.encodeToString(value.encodeToByteArray(), Base64.NO_WRAP)}")
            }
        }.build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                // OpenCode 2 changes session paths and prompt acknowledgement
                // semantics. Never replay prompts while probing an unknown API.
                if (response.code == 404 && nativeV2ServerInfo()) {
                    throw IOException(
                        "OpenCode 2 server detected; RikkaHub's workspace client " +
                            "still uses the V1 session API. Use the separate " +
                            "inference provider or a verified V2 adapter."
                    )
                }
                throw IOException("OpenCode HTTP ${response.code}: ${text.take(300)}")
            }
            if (text.isBlank()) JsonObject(emptyMap()) else JsonInstant.decodeFromString<JsonElement>(text)
        }
    }

    /** V1 can serve HTML on /api/info; only JSON V2 server-info
     * counts. This read-only probe runs solely after a V1 404.
     */
    private fun nativeV2ServerInfo(): Boolean = runCatching {
        val probe = Request.Builder().url("$baseUrl/api/info").get().apply {
            credential?.let { value ->
                header("Authorization", if (value.startsWith("Basic ") || value.startsWith("Bearer "))
                    value else "Basic ${Base64.encodeToString(value.encodeToByteArray(), Base64.NO_WRAP)}")
            }
            header("Accept", "application/json")
        }.build()
        client.newCall(probe).execute().use { response ->
            if (!response.isSuccessful ||
                response.body.contentType()?.subtype != "json"
            ) return@use false
            val info = JsonInstant.decodeFromString<JsonElement>(
                response.body.string().take(131072)
            ) as? JsonObject ?: return@use false
            (info["urls"] is JsonArray) &&
                (info.containsKey("ready") || info.containsKey("paths"))
        }
    }.getOrDefault(false)

    private fun directoryQuery(directory: String?): Map<String, String> =
        directory?.takeIf { it.isNotBlank() }?.let { mapOf("directory" to it) } ?: emptyMap()

    private fun JsonElement.arrayItems(): List<JsonObject> = when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(this)
        else -> emptyList()
    }

    private fun JsonObject.toSession() = OpenCodeSession(
        id = string("id") ?: error("OpenCode session has no id"),
        title = string("title"),
        directory = string("directory") ?: string("worktree"),
    )

    private fun JsonObject.toMessageOrNull(): OpenCodeMessage? {
        // OpenCode's session message API returns { info: { id, role, ... }, parts: [...] }.
        // Accept the older flat shape too, but prefer the canonical nested metadata.
        val info = get("info") as? JsonObject
        val id = info?.string("id") ?: string("id") ?: return null
        val role = info?.string("role") ?: string("role") ?: "assistant"
        val text = string("text") ?: (get("parts") as? JsonArray)?.mapNotNull { element ->
            val part = element as? JsonObject ?: return@mapNotNull null
            if (part.string("type") == "text") part.string("text") else null
        }?.joinToString("") ?: return null
        return OpenCodeMessage(id, role, text)
    }

    private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.boolean(key: String): Boolean? = get(key)?.jsonPrimitive?.booleanOrNull
    private fun String.pathSegment() = java.net.URLEncoder.encode(this, "UTF-8")
}
