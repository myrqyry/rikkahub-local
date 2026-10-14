package me.rerere.rikkahub.ui.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Only curated Colorway repository files can be downloaded by this browser. */
data class ColorwayCatalogEntry(val path: String, val name: String) {
    val id: String get() = path
}

object ColorwayCatalog {
    private const val TREE = "https://api.github.com/repos/myrqyry/colorway/git/trees/main?recursive=1"
    private const val RAW = "https://raw.githubusercontent.com/myrqyry/colorway/main/"
    private val pathFormat = Regex("""^public/themes/Colorway-[a-zA-Z0-9_-]+\.ovt$""")
    private val json = Json { ignoreUnknownKeys = true }

    /** Parse the public Git tree without ever accepting its URLs as download targets. */
    fun parseTree(text: String): List<ColorwayCatalogEntry> {
        val tree = json.parseToJsonElement(text).jsonObject
        require(tree["truncated"]?.jsonPrimitive?.content != "true") { "Theme catalog is incomplete; retry later." }
        return tree["tree"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item.jsonObject
            val path = obj["path"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (obj["type"]?.jsonPrimitive?.content != "blob" || !pathFormat.matches(path)) return@mapNotNull null
            val name = path.substringAfterLast("/").removePrefix("Colorway-").removeSuffix(".ovt")
                .replace('-', ' ').replace('_', ' ')
            ColorwayCatalogEntry(path, name)
        }.distinctBy { it.path }.sortedBy { it.name.lowercase() }
    }

    fun fetchCatalog(): List<ColorwayCatalogEntry> = parseTree(readText(TREE, 768 * 1024, "api.github.com"))

    fun download(entry: ColorwayCatalogEntry): CustomTheme {
        require(pathFormat.matches(entry.path)) { "Invalid theme file path." }
        val text = readText(RAW + entry.path, 96 * 1024, "raw.githubusercontent.com")
        return ThemeImport.parse(text).copy(source = "Colorway", sourceKey = entry.id)
    }

    private fun readText(address: String, maxBytes: Int, expectedHost: String): String {
        val url = URL(address)
        require(url.protocol == "https" && url.host == expectedHost) { "Unexpected theme download host." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "RikkaHub-Local-ThemeBrowser")
        }
        try {
            if (connection.responseCode != 200) {
                throw IllegalStateException("Theme server returned HTTP \${connection.responseCode}.")
            }
            if (connection.contentLengthLong > maxBytes) {
                throw IllegalArgumentException("Theme file is too large.")
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maxBytes) throw IllegalArgumentException("Theme file is too large.")
                    output.write(buffer, 0, count)
                }
            }
            return output.toString(StandardCharsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }
}
