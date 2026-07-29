package me.rerere.rikkahub.skills

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Resolves skills.sh-style URLs into a parsed [SkillDefinition].
 *
 * Accepts two input shapes:
 *  - `https://skills.sh/<slug>` — standard HTTP URL
 *  - `sk:<slug>` — shorthand that expands to `https://skills.sh/<slug>`
 *
 * The resolver fetches the raw JSON from the resolved URL, validates it against
 * the expected schema, and returns a [SkillDefinition] on success.
 */
class SkillUrlResolver(
    private val httpClient: OkHttpClient = defaultClient(),
) {
    /**
     * Main entry point. Parses [url], fetches the remote JSON, validates the
     * structure, and returns a [SkillDefinition] on success or null on failure.
     */
    fun resolve(url: String): SkillDefinition? {
        val resolved = expandUrl(url) ?: return null
        if (!isValidUrl(resolved)) return null
        val raw = try {
            fetch(resolved)
        } catch (_: Exception) {
            return null
        }
        return try {
            skillJson.decodeFromString(SkillDefinition.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Expand `sk:<slug>` shorthand to `https://skills.sh/<slug>`. Passes
     * http/https URLs through unchanged. Returns null for unrecognised formats.
     */
    private fun expandUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.startsWith("sk:")) {
            val slug = trimmed.removePrefix("sk:").trim('/')
            if (slug.isBlank() || !isValidSlug(slug)) return null
            return "https://skills.sh/$slug"
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return null
    }

    /**
     * URL guard: only http(s), no loopback, no localhost, no 0.0.0.0.
     * Mirrors the guard pattern from [SkillUrlImporter.checkUrl].
     */
    private fun isValidUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        return !isLoopback(host)
    }

    private fun isLoopback(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h == "localhost" || h.endsWith(".localhost")) return true
        if (h == "0.0.0.0" || h == "127.0.0.1" || h == "::1") return true
        if (h.startsWith("[") && h.endsWith("]")) {
            return isLoopback(h.removePrefix("[").removeSuffix("]"))
        }
        return runCatching {
            java.net.InetAddress.getByName(h).isLoopbackAddress
        }.getOrDefault(false)
    }

    private fun isValidSlug(slug: String): Boolean =
        slug.matches(Regex("""^[a-z0-9]([a-z0-9_-]*[a-z0-9])?$"""))

    private fun fetch(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "rikkahub-agent/skill-resolver")
            .header("Accept", "application/json")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}")
            }
            return resp.body.string()
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
            .also { me.rerere.rikkahub.utils.NetworkChangeMonitor.register(it) }
    }
}

/**
 * Schema for a skill definition fetched from a skills.sh-style URL.
 *
 * All fields are optional at the JSON level so a malformed remote response
 * gracefully deserialises into a partial object rather than throwing.
 */
@Serializable
data class SkillDefinition(
    val name: String = "",
    val slug: String = "",
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val tools: List<String> = emptyList(),
    val workflows: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    @SerialName("source_url")
    val sourceUrl: String = "",
)

private val skillJson = Json { ignoreUnknownKeys = true }