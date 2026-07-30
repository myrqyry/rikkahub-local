package me.rerere.rikkahub.skills.plugins

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.rerere.workspace.WorkspaceManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

class PluginManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val httpClient: OkHttpClient,
    private val registry: SlashCommandRegistry,
) {
    private val pluginsDir: File get() = File(context.filesDir, "plugins")
    private val registeredCommands = mutableMapOf<String, MutableList<String>>()
    private val adapter = PluginToolAdapter()

    private val _hookFlow = MutableSharedFlow<PluginHookEvent>(extraBufferCapacity = 16)
    val hookFlow: SharedFlow<PluginHookEvent> = _hookFlow.asSharedFlow()

    fun emitHook(event: PluginHookEvent) {
        _hookFlow.tryEmit(event)
    }

    fun getInstalledPlugins() = pluginsDir
        .takeIf { it.exists() }
        ?.listFiles()
        ?.filter { it.isDirectory }
        ?.mapNotNull { dir ->
            val manifestFile = File(dir, "plugin.json")
            if (!manifestFile.exists()) return@mapNotNull null
            PluginManifest.parse(manifestFile.readText()).getOrNull()?.let { manifest ->
                PluginBrief(
                    name = manifest.name,
                    description = manifest.description,
                    version = manifest.version,
                    author = manifest.author,
                    hasCommands = manifest.commands.isNotEmpty(),
                    hasTools = manifest.tools.isNotEmpty(),
                )
            }
        } ?: emptyList()

    data class PluginBrief(
        val name: String,
        val description: String,
        val version: String,
        val author: String,
        val hasCommands: Boolean,
        val hasTools: Boolean,
    )

    suspend fun installFromUrl(input: String): Result<Unit> = runCatching {
        val (owner, repo) = parsePluginRef(input.trim())

        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/zipball")
                .header("Accept", "application/vnd.github+json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                error("GitHub API ${response.code}: ${response.message}")
            }
            val body = response.body.bytes()

            val tempDir = File(context.cacheDir, "plugin_${repo}")
            tempDir.deleteRecursively()
            tempDir.mkdirs()

            ZipInputStream(body.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val target = File(tempDir, entry.name)
                    if (entry.isDirectory) target.mkdirs()
                    else target.outputStream().use { zis.copyTo(it) }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val manifestFile = tempDir.walkTopDown().find { it.name == "plugin.json" }
                ?: error("plugin.json not found in plugin archive")

            val manifest = PluginManifest.parse(manifestFile.readText()).getOrThrow()

            val pluginDir = File(pluginsDir, manifest.name)
            pluginDir.deleteRecursively()
            pluginDir.mkdirs()
            tempDir.walkTopDown().forEach { src ->
                val dest = File(pluginDir, src.relativeTo(tempDir).path)
                if (src.isDirectory) dest.mkdirs()
                else src.copyTo(dest, overwrite = true)
            }
            tempDir.deleteRecursively()

            registerPluginCommands(manifest)
        }
    }

    suspend fun uninstall(name: String) {
        withContext(Dispatchers.IO) {
            File(pluginsDir, name).deleteRecursively()
        }
        registeredCommands[name]?.forEach { registry.unregister(it) }
        registeredCommands.remove(name)
    }

    fun isInstalled(name: String): Boolean = File(pluginsDir, name).exists()

    fun createPluginTools(
        workspaceId: String,
        workspaceRepository: WorkspaceRepository,
    ): List<Tool> {
        return pluginsDir
            .takeIf { it.exists() }
            ?.listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { dir ->
                val manifestFile = File(dir, "plugin.json")
                if (!manifestFile.exists()) return@flatMap emptyList()
                val manifest = PluginManifest.parse(manifestFile.readText()).getOrNull()
                    ?: return@flatMap emptyList()
                manifest.tools.filter { it.rikkahubCommand != null }.map { toolDef ->
                    adapter.adapt(toolDef, manifest.name) { input ->
                        runPluginTool(toolDef, input, workspaceId, workspaceRepository)
                    }
                }
            } ?: emptyList()
    }

    private suspend fun runPluginTool(
        toolDef: PluginToolDef,
        input: JsonElement,
        workspaceId: String,
        workspaceRepository: WorkspaceRepository,
    ): List<UIMessagePart> {
        val result = workspaceRepository.executeCommand(
            id = workspaceId,
            command = toolDef.rikkahubCommand!!,
            stdin = input.toString().toByteArray(),
        )
        return listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("exitCode", result.exitCode)
                    put("stdout", result.stdout)
                    put("stderr", result.stderr)
                    if (result.timedOut) put("timedOut", true)
                    if (result.truncated) put("truncated", true)
                }.toString()
            )
        )
    }

    companion object {
        fun parsePluginRef(input: String): Pair<String, String> {
            val ref = input.trim().removePrefix("claude plugin install ").trim()
            val githubUrl = Regex("https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?$")
            githubUrl.find(ref)?.let {
                return it.groupValues[1] to it.groupValues[2]
            }
            val market = Regex("^([^@]+)@([^@]+)$")
            market.find(ref)?.let {
                return it.groupValues[2] to it.groupValues[1]
            }
            val simple = Regex("^([^/]+)/([^/]+)$")
            simple.find(ref)?.let {
                return it.groupValues[1] to it.groupValues[2]
            }
            error("Usage: name@org (e.g. telegram@claude-plugins-official) or org/repo or GitHub URL")
        }
    }

    private fun registerPluginCommands(manifest: PluginManifest) {
        val cmdNames = mutableListOf<String>()
        manifest.commands.forEach { cmd ->
            val qualifiedName = "${manifest.name}:${cmd.name}"
            registry.register(
                SlashCommand(
                    name = qualifiedName,
                    description = cmd.description,
                    args = cmd.args.map { SlashCommandArg(it.name, it.description, it.required) },
                    handler = { Result.success("Plugin command /$qualifiedName executed") },
                )
            )
            cmdNames.add(qualifiedName)
        }
        registeredCommands[manifest.name] = cmdNames
    }
}
