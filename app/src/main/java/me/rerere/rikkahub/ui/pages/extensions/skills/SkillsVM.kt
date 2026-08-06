package me.rerere.rikkahub.ui.pages.extensions.skills

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.catalog.mapInstallResult
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillFrontmatterParser
import me.rerere.rikkahub.data.files.SkillMetadata
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.rerere.rikkahub.skills.CatalogEntry
import me.rerere.rikkahub.skills.SkillCatalog
import me.rerere.rikkahub.skills.SkillUrlImporter
import me.rerere.rikkahub.skills.SkillZipError
import me.rerere.rikkahub.skills.SkillZipImporter
import me.rerere.rikkahub.skills.loadCatalogFromAssets
import me.rerere.rikkahub.skills.imports.ImportCandidate
import me.rerere.rikkahub.skills.imports.ImportCoordinator
import me.rerere.rikkahub.skills.imports.ImportOrigin
import me.rerere.rikkahub.skills.imports.ImportRequest
import me.rerere.rikkahub.skills.imports.ImportResult
import me.rerere.rikkahub.skills.imports.import
import me.rerere.rikkahub.skills.imports.ArtifactKind
import java.io.File
import java.nio.file.Files
import kotlin.collections.iterator

class SkillsVM(
    private val context: Context,
    private val skillManager: SkillManager,
    private val urlImporter: SkillUrlImporter,
    private val importCoordinator: ImportCoordinator,
) : ViewModel() {

    companion object {
        private const val TAG = "SkillsVM"
        private const val MAX_MD_BYTES = 1L * 1024 * 1024 // 1 MB cap on local .md
    }
    private val _skills = MutableStateFlow<List<SkillMetadata>>(emptyList())
    val skills = _skills.asStateFlow()

    /**
     * Phase 19D — flow-derived snapshot of currently-installed skill names. The catalog
     * sheet observes this so the "Install" / "Installed" button state stays in sync as
     * the user (or LLM) installs / deletes skills.
     */
    val installedSkillNames = _skills
        .map { list -> list.mapTo(mutableSetOf()) { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Phase 19D — bundled catalog. Loaded lazily on first access. */
    val catalog: SkillCatalog by lazy { loadCatalogFromAssets(context) }

    init {
        loadSkills()
    }

    private fun loadSkills() {
        viewModelScope.launch(Dispatchers.IO) {
            _skills.value = skillManager.listSkills()
        }
    }

    fun saveSkill(name: String, content: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = skillManager.saveSkill(name, content)
            _skills.value = skillManager.listSkills()
            withContext(Dispatchers.Main) {
                onResult(result != null)
            }
        }
    }

    fun deleteSkill(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            skillManager.deleteSkill(name)
            _skills.value = skillManager.listSkills()
        }
    }

    fun getSkillsDir() = skillManager.getSkillsDir()

    /** Prepare a URL candidate without installing it; the page owns the review dialog. */
    fun prepareSkillImport(source: String, onResult: (Result<ImportCandidate>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = importCoordinator.prepare(source.trim(), ArtifactKind.SKILL)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun discardPrepared(candidate: ImportCandidate) {
        importCoordinator.discard(candidate)
    }

    /** Install exactly the candidate already reviewed, so preparation is never repeated. */
    fun installPreparedSkill(
        candidate: ImportCandidate,
        onResult: (Boolean, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = importCoordinator.install(candidate)
            _skills.value = skillManager.listSkills()
            val outcome = result.fold(
                onSuccess = { installed ->
                    val value = installed as ImportResult.Installed
                    true to buildString {
                        append(value.name)
                        value.warning?.let { append(" — $it") }
                    }
                },
                onFailure = { false to (it.message ?: "skill import failed") },
            )
            withContext(Dispatchers.Main) { onResult(outcome.first, outcome.second) }
        }
    }

    fun importSkillFromGitHub(repoUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val prepared = importCoordinator.prepare(repoUrl, ArtifactKind.SKILL)
            val result = prepared.fold(
                onSuccess = { importCoordinator.install(it) },
                onFailure = { Result.failure(it) },
            )
            _skills.value = skillManager.listSkills()
            val outcome = result.fold(
                onSuccess = { true to (it as ImportResult.Installed).name },
                onFailure = { false to (it.message ?: "Unknown error") },
            )
            withContext(Dispatchers.Main) { onResult(outcome.first, outcome.second) }
        }
    }

    /**
     * Phase 19C — install a skill from a local file picked via SAF (`OpenDocument`).
     *
     * Accepts:
     *  - `.md` / `.markdown` (or `text/markdown` MIME): read up to [MAX_MD_BYTES] of UTF-8
     *    text, then run through [SkillUrlImporter.importFromText] (same format detection
     *    + HTML guard + transcoder pipeline as the GitHub URL path).
     *  - `.zip` (or `application/zip` MIME): extract via [SkillZipImporter] to a temp dir
     *    inside the app's cache, locate the SKILL.md, copy every file inside that root
     *    into the SkillManager via [SkillManager.saveSkillFilesAtomically].
     *
     * On failure, [onResult] receives `false` + a localised-string-key (`skill_import_*`)
     * the UI looks up via stringResource. On success, [onResult] receives `true` + the
     * installed skill's name.
     */
    fun importFromLocalFile(uri: Uri, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val type = detectFileType(uri)
            try {
                val outcome: Pair<Boolean, String> = when (type) {
                    LocalFileType.Markdown -> importLocalMarkdown(uri)
                    LocalFileType.Zip -> importLocalZip(uri)
                    LocalFileType.Unsupported -> false to "skill_import_unsupported_file_type"
                }
                _skills.value = skillManager.listSkills()
                withContext(Dispatchers.Main) { onResult(outcome.first, outcome.second) }
            } catch (t: Throwable) {
                Log.w(TAG, "importFromLocalFile failed for $uri", t)
                withContext(Dispatchers.Main) {
                    onResult(false, t.message ?: "skill_import_unsupported_file_type")
                }
            }
        }
    }

    /**
     * Phase 19D — install a skill from a [CatalogEntry].
     *
     * If the entry is `is_bundled = true`, this is a no-op (the skill is already on disk
     * via [SkillManager.seedDefaultSkillsIfNeeded]) and we return success immediately so
     * the UI flips its row to "Installed". Otherwise [CatalogEntry.sourceUrl] is fetched
     * via [ImportCoordinator], so preparation and installation share the same provenance path.
     */
    fun installFromCatalog(entry: CatalogEntry, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (entry.isBundled) {
                _skills.value = skillManager.listSkills()
                withContext(Dispatchers.Main) { onResult(true, entry.name) }
                return@launch
            }
            val url = entry.sourceUrl
            if (url.isNullOrBlank()) {
                withContext(Dispatchers.Main) { onResult(false, "skill_catalog_install_failed") }
                return@launch
            }
            val request = ImportRequest(
                source = url,
                expectedKind = ArtifactKind.SKILL,
                expectedSha256 = null,
                origin = ImportOrigin.Catalog(entry.name),
            )
            val (ok, message) = mapInstallResult(importCoordinator.import(request), entry.name)
            _skills.value = skillManager.listSkills()
            withContext(Dispatchers.Main) { onResult(ok, message) }
        }
    }

    private enum class LocalFileType { Markdown, Zip, Unsupported }

    private fun detectFileType(uri: Uri): LocalFileType {
        val mime = context.contentResolver.getType(uri)?.lowercase()
        if (mime != null) {
            if (mime == "text/markdown" || mime == "text/x-markdown" || mime == "text/plain") {
                return LocalFileType.Markdown
            }
            if (mime == "application/zip" || mime == "application/x-zip-compressed") {
                return LocalFileType.Zip
            }
        }
        // Fall back to the displayed filename. Some pickers (e.g. Files by Google) don't
        // attach a MIME type for `.md` and surface it as `application/octet-stream`.
        val name = queryDisplayName(uri)?.lowercase().orEmpty()
        return when {
            name.endsWith(".md") || name.endsWith(".markdown") -> LocalFileType.Markdown
            name.endsWith(".zip") -> LocalFileType.Zip
            else -> LocalFileType.Unsupported
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private fun importLocalMarkdown(uri: Uri): Pair<Boolean, String> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            // Read up to MAX_MD_BYTES + 1 to detect overflow without materialising the
            // whole stream blindly.
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_MD_BYTES) {
                    // Use the markdown-specific cap error, not the zip cap key.
                    return false to "skill_import_md_too_large"
                }
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: return false to "skill_import_unsupported_file_type"
        val text = bytes.toString(Charsets.UTF_8)
        if (text.isBlank()) {
            return false to "skill_import_empty_file"
        }
        val sourceLabel = queryDisplayName(uri) ?: "local_file"
        val result = urlImporter.importFromText(text, sourceLabel = sourceLabel)
        return when (result) {
            is SkillUrlImporter.Result.Ok -> true to result.metadata.name
            is SkillUrlImporter.Result.Err -> false to result.detail
        }
    }

    private fun importLocalZip(uri: Uri): Pair<Boolean, String> {
        // Extract into a uniquely-named temp dir under cache so we never collide with
        // another import-in-flight. We delete it on success or failure.
        val tempRoot = File(context.cacheDir, "skill-zip-import")
        tempRoot.mkdirs()
        val workDir = Files.createTempDirectory(tempRoot.toPath(), "extract-").toFile()
        try {
            val skillRoot = context.contentResolver.openInputStream(uri)?.use { input ->
                SkillZipImporter.extractZipToDir(input, workDir)
            } ?: return false to "skill_import_unsupported_file_type"
            if (skillRoot.isFailure) {
                val err = skillRoot.exceptionOrNull()
                val key = when (err) {
                    is SkillZipError.MissingSkillMd -> "skill_import_missing_skill_md"
                    is SkillZipError.PathTraversal -> "skill_import_path_traversal"
                    is SkillZipError.TooLarge -> "skill_import_zip_too_large"
                    else -> "skill_import_unsupported_file_type"
                }
                return false to key
            }
            val rootDir = skillRoot.getOrThrow()
            val skillMd = rootDir.resolve("SKILL.md").takeIf { it.exists() }
                ?: rootDir.listFiles()?.firstOrNull { it.isFile && it.name.equals("SKILL.md", ignoreCase = true) }
                ?: return false to "skill_import_missing_skill_md"
            val frontmatter = SkillFrontmatterParser.parse(skillMd.readText())
            val skillName = frontmatter["name"]?.takeIf { it.isNotBlank() }
                ?: return false to "skill_import_missing_skill_md"
            // Collect every file into a relativePath -> content map, then atomic-save.
            val files = LinkedHashMap<String, String>()
            rootDir.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(rootDir).path.replace(File.separatorChar, '/')
                files[rel] = f.readText()
            }
            val saved = skillManager.saveSkillFilesAtomically(skillName, files)
            return if (saved) true to skillName else false to "skill_import_unsupported_file_type"
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }

}
