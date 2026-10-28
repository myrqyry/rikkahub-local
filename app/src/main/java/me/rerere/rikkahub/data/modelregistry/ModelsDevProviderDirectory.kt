package me.rerere.rikkahub.data.modelregistry

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.ProviderSetting
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Public models.dev metadata is for discovery only. Never treat catalog presence as proof of a
 * working endpoint or authorization to send a user's credentials to it.
 */
data class ModelsDevProviderEntry(
    val id: String,
    val name: String,
    val baseUrl: String?,
    val modelCount: Int,
    val canAddAsOpenAiCompatible: Boolean,
) {
    fun toProviderDraft(): ProviderSetting.OpenAI {
        require(canAddAsOpenAiCompatible && baseUrl != null) { "Unsupported provider adapter" }
        return ProviderSetting.OpenAI(
            name = name,
            baseUrl = baseUrl,
            apiKey = "",
            enabled = false,
            builtIn = false,
        )
    }
}

data class ModelsDevDirectorySnapshot(
    val providers: List<ModelsDevProviderEntry>,
    val fromCache: Boolean,
    val stale: Boolean,
)

object ModelsDevProviderDirectory {
    const val SOURCE_URL = "https://models.dev/api.json"
    private const val MAX_CATALOG_BYTES = 12_000_000L
    private const val CACHE_AGE_MS = 24L * 60 * 60 * 1000
    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun parse(json: String): List<ModelsDevProviderEntry> {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_CATALOG_BYTES) { "Catalog too large" }
        val root = Json.parseToJsonElement(json) as? JsonObject
            ?: throw IllegalArgumentException("Expected a provider catalog object")
        require(root.isNotEmpty()) { "Empty provider catalog" }
        return root.mapNotNull { (key, value) ->
            val provider = value as? JsonObject ?: return@mapNotNull null
            val suppliedId = provider["id"]?.jsonPrimitive?.contentOrNull
            if (suppliedId != null && suppliedId != key) return@mapNotNull null
            val name = provider["name"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= 120 } ?: return@mapNotNull null
            val api = provider["api"]?.jsonPrimitive?.contentOrNull
            val endpoint = api?.toHttpUrlOrNull()
            val validEndpoint = endpoint != null && (
                endpoint.isHttps || (endpoint.scheme == "http" && endpoint.host in setOf(
                    "localhost", "127.0.0.1", "::1"
                ))
            )
            val compatible = provider["npm"]?.jsonPrimitive?.contentOrNull ==
                "@ai-sdk/openai-compatible" && validEndpoint
            ModelsDevProviderEntry(
                id = key,
                name = name,
                baseUrl = api,
                modelCount = (provider["models"] as? JsonObject)?.size ?: 0,
                canAddAsOpenAiCompatible = compatible,
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /**
     * Refresh at most daily, retaining the last successfully parsed catalog for offline browsing.
     * Cache belongs to this public directory only; user provider settings are never read or written.
     */
    fun load(
        cacheFile: File,
        fetch: () -> String = ::fetchLive,
        nowMillis: Long = System.currentTimeMillis(),
    ): ModelsDevDirectorySnapshot {
        fun cached(): List<ModelsDevProviderEntry>? =
            try { if (cacheFile.isFile) parse(cacheFile.readText()) else null }
            catch (_: Exception) { null }

        val cacheAge = nowMillis - cacheFile.lastModified()
        val isFresh = cacheFile.isFile && cacheAge in -60_000L until CACHE_AGE_MS
        if (isFresh) {
            cached()?.let { return ModelsDevDirectorySnapshot(it, fromCache = true, stale = false) }
        }

        return try {
            val content = fetch()
            val providers = parse(content)
            val temp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            try {
                temp.writeText(content)
                if (!temp.renameTo(cacheFile)) temp.delete()
            } catch (_: IOException) {
                temp.delete()
            }
            ModelsDevDirectorySnapshot(providers, fromCache = false, stale = false)
        } catch (e: Exception) {
            cached()?.let { ModelsDevDirectorySnapshot(it, fromCache = true, stale = true) }
                ?: throw IOException("Cannot load models.dev provider directory", e)
        }
    }

    private fun fetchLive(): String {
        val request = Request.Builder()
            .url(SOURCE_URL)
            .header("User-Agent", "RikkaHub-Local/1.0 (provider catalog)")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Catalog HTTP ${response.code}")
            val body = response.body
            if (body.contentLength() > MAX_CATALOG_BYTES) throw IOException("Catalog too large")
            val source = body.source()
            source.request(MAX_CATALOG_BYTES + 1)
            if (source.buffer.size > MAX_CATALOG_BYTES) throw IOException("Catalog too large")
            return source.readUtf8()
        }
    }
}
