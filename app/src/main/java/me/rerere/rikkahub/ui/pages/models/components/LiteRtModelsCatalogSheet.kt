package me.rerere.rikkahub.ui.pages.models.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.pages.modelmanager.LiteRtGalleryModel
import me.rerere.rikkahub.ui.pages.modelmanager.LiteRtGalleryAsset
import me.rerere.rikkahub.ui.pages.modelmanager.LiteRtModelsGallery
import me.rerere.rikkahub.ui.pages.modelmanager.ModelManagerViewModel
import me.rerere.rikkahub.ui.pages.modelmanager.PpOcrVerifiedPackage
import me.rerere.rikkahub.data.rag.EmbeddingGemma2Variant

/** Curated upstream model list, with actual artifact discovery before each download. */
@Composable
internal fun LiteRtModelsCatalogSheet(
    viewModel: ModelManagerViewModel,
    downloadInProgress: Boolean,
) {
    val repositoryState by viewModel.galleryDiscovery.collectAsStateWithLifecycle()
    // Re-read file status after an asset is removed, even if model selection is unchanged.
    val galleryVersion by viewModel.galleryVersion.collectAsStateWithLifecycle()
    val ppOcrSelected by viewModel.ppOcrSelected.collectAsStateWithLifecycle()
    val embeddingGemma2SelectedVariant by viewModel.embeddingGemma2SelectedVariant.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<LiteRtGalleryModel?>(null) }
    var deleting by remember { mutableStateOf<LiteRtGalleryAsset?>(null) }
    var removeVerifiedOcr by remember { mutableStateOf(false) }
    var removeEmbeddings by remember { mutableStateOf<EmbeddingGemma2Variant?>(null) }

    Column(
        // The verified package controls can exceed a phone-height sheet. Give
        // the whole page a bounded, scrollable viewport (not just its gallery).
        modifier = Modifier.fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "LiteRT-Models · ${viewModel.liteRtGallery.size} entries",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Community model collection. LiteRT-LM chat bundles can be registered after download; " +
                "other files are stored separately until their task-specific inference adapter is available.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selected == null) {
            // Functional PP-OCRv5 path, separate from the general source-only
            // catalog. Three revision-pinned and hashed files are required.
            val ocrReady = remember(context, galleryVersion, downloadInProgress) {
                PpOcrVerifiedPackage.present(context)
            }
            Text("PP-OCRv5 · verified OCR package", style = MaterialTheme.typography.titleSmall)
            Text(
                "Google LiteRT Model Zoo: detector, recognizer and text dictionary " +
                    "(~26 MiB). Downloads are pinned and SHA-256 checked. " +
                    "Enabling OCR requires an explicit second tap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!ocrReady) {
                Button(
                    onClick = viewModel::downloadVerifiedPpOcr,
                    enabled = !downloadInProgress,
                ) { Text("Download verified PP-OCRv5 package") }
            } else if (ppOcrSelected) {
                Text(
                    "PP-OCRv5 is selected as your on-device OCR model.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Button(
                    onClick = viewModel::selectVerifiedPpOcr,
                    enabled = !downloadInProgress,
                ) { Text("Use PP-OCRv5 for OCR") }
                TextButton(
                    onClick = { removeVerifiedOcr = true },
                    enabled = !downloadInProgress,
                ) { Text("Remove downloaded OCR package") }
            }
            Text("EmbeddingGemma 2 · pinned local models", style = MaterialTheme.typography.titleSmall)
            Text(
                "Three separate LiteRT models: text, text + vision, or full text + vision + audio. " +
                    "Choose a model explicitly; existing RAG vectors are kept in their own spaces.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EmbeddingGemma2Variant.entries.forEach { variant ->
                val ready = remember(context, galleryVersion, downloadInProgress, variant) {
                    variant.present(context)
                }
                val fileExists = remember(context, galleryVersion, downloadInProgress, variant) {
                    variant.file(context).exists()
                }
                val active = embeddingGemma2SelectedVariant == variant
                val variantState = embeddingGemma2CatalogState(
                    selected = active,
                    fileExists = fileExists,
                    expectedSize = ready,
                )
                val variantName = when (variant) {
                    EmbeddingGemma2Variant.TEXT -> "270M · Text/code"
                    EmbeddingGemma2Variant.VISION -> "440M · Text, images, PDF pages, video frames"
                    EmbeddingGemma2Variant.FULL -> "740M · Text, images, PDF pages, video frames, WAV"
                }
                val shortName = variantName.substringBefore(" ·")
                Text(
                    "$variantName · ${variant.sizeBytes / (1024 * 1024)} MiB · 768 dimensions",
                    style = MaterialTheme.typography.bodyMedium,
                )
                when (variantState) {
                    EmbeddingGemma2CatalogState.NOT_DOWNLOADED,
                    EmbeddingGemma2CatalogState.SELECTED_MISSING -> {
                        if (active) Text(
                            "Selected for RAG, but its model file is missing. Download it again or switch back to default.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { viewModel.downloadEmbeddingGemma2(variant) },
                            enabled = !downloadInProgress,
                        ) { Text("Download $shortName") }
                    }
                    EmbeddingGemma2CatalogState.INCOMPLETE,
                    EmbeddingGemma2CatalogState.SELECTED_INCOMPLETE -> {
                        Text(
                            if (active) "Selected model file is incomplete. Switch back or remove the incomplete file."
                            else "Incomplete model file. Remove it before downloading again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!active) TextButton(
                            onClick = { removeEmbeddings = variant },
                            enabled = !downloadInProgress,
                        ) { Text("Remove incomplete download") }
                    }
                    EmbeddingGemma2CatalogState.SELECTED_AVAILABLE -> {
                        Text(
                            "Selected for local RAG. Other model-space vectors are preserved; reindex as needed.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    EmbeddingGemma2CatalogState.AVAILABLE -> {
                        Button(
                            onClick = { viewModel.selectEmbeddingGemma2(variant) },
                            enabled = !downloadInProgress,
                        ) { Text("Use $shortName for RAG") }
                        TextButton(
                            onClick = { removeEmbeddings = variant },
                            enabled = !downloadInProgress,
                        ) { Text("Remove $shortName download") }
                    }
                }
                if (active) {
                    TextButton(
                        onClick = viewModel::chooseDefaultRagEmbeddings,
                        enabled = !downloadInProgress,
                    ) { Text("Return to default RAG embedding choice") }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find a model or task") },
                singleLine = true,
            )
            val entries = remember(query, viewModel.liteRtGallery) {
                viewModel.liteRtGallery.filter { entry ->
                    query.isBlank() ||
                        entry.name.contains(query, ignoreCase = true) ||
                        entry.task.contains(query, ignoreCase = true)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(entries, key = { it.slug }) { entry ->
                    GalleryRow(
                        title = entry.name,
                        details = listOf(entry.task, entry.reportedPerformance, entry.device)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        action = "Files",
                        enabled = true,
                        onClick = {
                            selected = entry
                            viewModel.inspectGalleryModel(entry)
                        },
                    )
                }
            }
        } else {
            val entry = selected!!
            TextButton(onClick = { selected = null }) { Text("← All models") }
            Text(entry.name, style = MaterialTheme.typography.titleMedium)
            Text(
                entry.task + listOf(entry.reportedPerformance, entry.device)
                    .filter { it.isNotBlank() }.joinToString(" · ", prefix = " · ").trimEnd(' ', '·'),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { uriHandler.openUri(entry.sourceUrl) }) {
                Text("View original model page / scripts")
            }
            if (repositoryState.modelSlug != entry.slug || repositoryState.loading) {
                CircularProgressIndicator()
            } else if (repositoryState.error != null) {
                Text(
                    repositoryState.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { viewModel.inspectGalleryModel(entry) }) {
                    Text("Retry file lookup")
                }
            } else if (repositoryState.assets.isEmpty()) {
                Text(
                    "No directly downloadable model assets are advertised by this source. " +
                        "The original project may provide conversion scripts or a demo only.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "Select an actual published file. Multiple files may be needed for a working model.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(repositoryState.assets, key = { it.downloadUrl }) { asset ->
                        // galleryVersion invalidates this lightweight file-existence read.
                        val stored = remember(entry, asset, galleryVersion, downloadInProgress) {
                            viewModel.galleryFileDownloaded(entry, asset)
                        }
                        GalleryRow(
                            title = asset.path,
                            details = buildString {
                                asset.sizeBytes?.let { size ->
                                    append(formatModelSize(size))
                                    append(" · ")
                                }
                                append(if (asset.isRunnableLiteRtLm) {
                                    "LiteRT-LM · chat (device verification required)"
                                } else {
                                    "File only · task adapter not connected"
                                })
                                if (asset.sha256 != null) append(" · SHA-256 provided")
                            },
                            action = when {
                                stored && !asset.isRunnableLiteRtLm -> "Remove"
                                stored -> "Installed"
                                else -> "Download"
                            },
                            enabled = !downloadInProgress && (!stored || !asset.isRunnableLiteRtLm),
                            onClick = {
                                if (stored) deleting = asset
                                else viewModel.downloadGalleryAsset(entry, asset)
                            },
                        )
                    }
                }
            }
        }
    }
    removeEmbeddings?.let { variant ->
        AlertDialog(
            onDismissRequest = { removeEmbeddings = null },
            title = { Text("Remove EmbeddingGemma 2 ${variant.name}?") },
            text = { Text("Delete only this pinned model file? Existing RAG vectors stay in the database.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeEmbeddingGemma2(variant)
                    removeEmbeddings = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removeEmbeddings = null }) { Text("Cancel") }
            },
        )
    }
    if (removeVerifiedOcr) {
        AlertDialog(
            onDismissRequest = { removeVerifiedOcr = false },
            title = { Text("Remove PP-OCRv5 package?") },
            text = { Text("Remove only the three verified PP-OCRv5 files from RikkaHub?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeVerifiedPpOcr()
                    removeVerifiedOcr = false
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removeVerifiedOcr = false }) { Text("Cancel") }
            },
        )
    }
    deleting?.let { asset ->
        val entry = selected
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove downloaded file?") },
            text = { Text("Delete only ${asset.path} from RikkaHub's gallery downloads?") },
            confirmButton = {
                TextButton(onClick = {
                    if (entry != null) viewModel.deleteGalleryAsset(entry, asset)
                    deleting = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GalleryRow(
    title: String,
    details: String,
    action: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                details,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onClick, enabled = enabled) { Text(action) }
    }
}

private fun formatModelSize(bytes: Long): String =
    if (bytes >= 1_073_741_824L) "%.2f GiB".format(java.util.Locale.US, bytes / 1_073_741_824.0)
    else "%.0f MiB".format(java.util.Locale.US, bytes / 1_048_576.0)
