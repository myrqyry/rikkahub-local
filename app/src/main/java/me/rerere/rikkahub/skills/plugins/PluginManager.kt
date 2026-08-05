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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.rerere.workspace.WorkspaceManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.ByteArrayInputStream
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
    private val _installedPlugins = MutableStateFlow<List<PluginBrief>>(emptyList())
    val installedPlugins: StateFlow<List<PluginBrief>> = _installedPlugins

    init {
        refreshInventory()
    }

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

    fun refreshInventory() {
        _installedPlugins.value = getInstalledPlugins()
    }

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
            val body = response.use {
                if (!it.isSuccessful) error("GitHub API ${it.code}: ${it.message}")
                it.body.bytes()
            }
            if (body.size > MAX_ARCHIVE_BYTES) {
                error("plugin archive exceeds ${MAX_ARCHIVE_BYTES / 1024 / 1024} MB")
            }

            val tempDir = File(context.cacheDir, "plugin_${repo}")
            tempDir.deleteRecursively()
            tempDir.mkdirs()

            try {
                var entryCount = 0
                var uncompressedBytes = 0L
                ZipInputStream(ByteArrayInputStream(body)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (++entryCount > MAX_ARCHIVE_ENTRIES) error("plugin archive has too many files")
                    val target = safeZipTarget(tempDir, entry.name)
                    if (entry.isDirectory) target.mkdirs()
                    else target.outputStream().use { out ->
                        val buffer = ByteArray(COPY_BUFFER)
                        while (true) {
                            val count = zis.read(buffer)
                            if (count <= 0) break
                            uncompressedBytes += count
                            if (uncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
                                error("plugin archive expands beyond ${MAX_UNCOMPRESSED_BYTES / 1024 / 1024} MB")
                            }
                            out.write(buffer, 0, count)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                }

                val manifests = tempDir.walkTopDown()
                .filter { it.isFile && it.name == "plugin.json" }
                .toList()
                if (manifests.size != 1) error("plugin archive must contain exactly one plugin.json")
                val manifestFile = manifests.single()

                val manifest = PluginManifest.parse(manifestFile.readText()).getOrThrow()
                require(PLUGIN_ID.matches(manifest.name)) { "invalid plugin id" }

                val sourceRoot = manifestFile.parentFile ?: error("plugin manifest has no parent")
                val stagingDir = File(pluginsDir, ".staging-${manifest.name}")
                stagingDir.deleteRecursively()
                stagingDir.mkdirs()
                sourceRoot.walkTopDown().forEach { src ->
                    val dest = File(stagingDir, src.relativeTo(sourceRoot).path)
                    if (src.isDirectory) dest.mkdirs() else src.copyTo(dest, overwrite = true)
                }
                val pluginDir = safePluginDir(manifest.name)
                val backupDir = File(pluginsDir, ".backup-${manifest.name}")
                backupDir.deleteRecursively()
                if (pluginDir.exists() && !pluginDir.renameTo(backupDir)) error("could not stage existing plugin")
                if (!stagingDir.renameTo(pluginDir)) {
                    backupDir.renameTo(pluginDir)
                    error("could not activate plugin")
                }
                backupDir.deleteRecursively()

                registerPluginCommands(manifest)
                refreshInventory()
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    suspend fun uninstall(name: String) {
        withContext(Dispatchers.IO) {
            File(pluginsDir, name).deleteRecursively()
        }
        registeredCommands[name]?.forEach { registry.unregister(it) }
        registeredCommands.remove(name)
        refreshInventory()
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
        private const val MAX_ARCHIVE_BYTES = 50L * 1024 * 1024
        private const val MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024
        private const val MAX_ARCHIVE_ENTRIES = 500
        private const val COPY_BUFFER = 8 * 1024
        private val PLUGIN_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")

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

    private fun safeZipTarget(root: File, entryName: String): File {
        require(entryName.isNotBlank()) { "empty archive entry" }
        require(!entryName.startsWith('/') && !entryName.startsWith('\\')) {
            "unsafe archive path"
        }
        val target = File(root, entryName)
        val rootPath = root.canonicalPath + File.separator
        require(target.canonicalPath.startsWith(rootPath)) { "unsafe archive path: $entryName" }
        return target
    }

    private fun safePluginDir(name: String): File {
        require(PLUGIN_ID.matches(name)) { "invalid plugin id" }
        val dir = File(pluginsDir, name)
        val rootPath = pluginsDir.canonicalPath + File.separator
        require(dir.canonicalPath.startsWith(rootPath)) { "unsafe plugin path" }
        return dir
    }

}
