package me.rerere.rikkahub.ui.pages.modelmanager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.locallm.SdCatalogEntry
import me.rerere.rikkahub.R
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerPage(
    // Koin registers this VM parameterised ({ params -> ... }) even though it takes no
    // parameters, so the type-less koinViewModel() overload can't match a definition and
    // the page crashed on open. The explicit type parameter picks the parameterised one.
    viewModel: ModelManagerViewModel = koinViewModel<ModelManagerViewModel>(),
) {
    var tab by remember { mutableStateOf(0) }
    val provider by viewModel.provider.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importModelFromUri(uri)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.model_manager_title)) }) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.model_manager_tab_installed)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.model_manager_tab_catalog)) },
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text(stringResource(R.string.model_manager_tab_hf_url)) },
                )
                Tab(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    text = { Text(stringResource(R.string.model_manager_tab_local_import)) },
                )
            }

            val sdProvider = provider
            when (tab) {
                0 -> InstalledTab(sdProvider?.models ?: emptyList(), viewModel)
                1 -> CatalogTab(viewModel, downloadProgress != null)
                2 -> HfUrlTab(viewModel, downloadProgress != null)
                3 -> LocalImportTab(filePickerLauncher, downloadProgress != null)
            }

            downloadProgress?.let { progress ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (progress.totalBytes != null && progress.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { progress.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = stringResource(R.string.local_llm_download_progress, progress.percent),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            errorMessage?.let { msg ->
                Text(
                    text = stringResource(R.string.local_llm_status_error_format, msg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.InstalledTab(
    models: List<me.rerere.ai.provider.Model>,
    viewModel: ModelManagerViewModel,
) {
    if (models.isEmpty()) {
        Text(
            text = stringResource(R.string.model_manager_no_models),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(models, key = { it.modelId }) { model ->
            InstalledModelRow(
                model = model,
                onRename = { newName -> viewModel.renameModel(model.modelId, newName) },
                onDelete = { viewModel.deleteModel(model.modelId) },
            )
        }
    }
}

@Composable
private fun ColumnScope.CatalogTab(
    viewModel: ModelManagerViewModel,
    downloadInProgress: Boolean,
) {
    val installedModels by viewModel.provider.collectAsStateWithLifecycle()
    val installedFiles = installedModels?.models?.map { it.modelId }?.toSet() ?: emptySet()

    LazyColumn(modifier = Modifier.weight(1f)) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.local_llm_catalog_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.model_manager_sd_catalog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(viewModel.catalogEntries, key = { it.modelFile }) { entry ->
            val context = LocalContext.current
            SdCatalogEntryCard(
                entry = entry,
                installed = entry.modelFile in installedFiles,
                onOpenSource = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, entry.sourceUrl.toUri()))
                },
            )
        }
    }
}

@Composable
private fun HfUrlTab(
    viewModel: ModelManagerViewModel,
    downloadInProgress: Boolean,
) {
    var manualUrl by remember { mutableStateOf("") }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        OutlinedTextField(
            value = manualUrl,
            onValueChange = { manualUrl = it },
            label = { Text(stringResource(R.string.local_llm_install_url_label)) },
            supportingText = { Text(stringResource(R.string.local_llm_install_url_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    viewModel.startManualDownload(manualUrl)
                    manualUrl = ""
                },
                enabled = manualUrl.isNotBlank() && !downloadInProgress,
            ) {
                Text(stringResource(R.string.local_llm_install_url_action))
            }
            OutlinedButton(
                onClick = { viewModel.startDefaultDownload() },
                enabled = !downloadInProgress,
            ) {
                Text(stringResource(R.string.local_llm_download_default))
            }
        }
    }
}

@Composable
private fun LocalImportTab(
    filePickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    downloadInProgress: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        OutlinedButton(
            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
            enabled = !downloadInProgress,
        ) {
            Text(stringResource(R.string.local_llm_import_filesystem))
        }
    }
}

@Composable
private fun SdCatalogEntryCard(
    entry: SdCatalogEntry,
    installed: Boolean,
    onOpenSource: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (entry.recommended) {
                    Text(
                        text = stringResource(R.string.local_llm_catalog_recommended),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Text(
                entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = String.format(
                    java.util.Locale.US,
                    stringResource(R.string.local_llm_catalog_size_format),
                    entry.sizeBytes / 1_000_000_000.0,
                    entry.minDeviceMemoryGb,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (installed) {
                    Text(
                        text = stringResource(R.string.local_llm_catalog_installed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(
                        onClick = onOpenSource,
                    ) {
                        Text(stringResource(R.string.local_llm_catalog_get_on_hf))
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledModelRow(
    model: me.rerere.ai.provider.Model,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var renameText by remember(model.id) { mutableStateOf(model.displayName) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (renaming) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                label = { Text(stringResource(R.string.local_llm_rename_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { renaming = false }) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        onRename(renameText)
                        renaming = false
                    },
                    enabled = renameText.isNotBlank() && renameText != model.displayName,
                ) {
                    Text(stringResource(R.string.local_llm_rename_save))
                }
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    model.modelId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { renaming = true }) {
                Icon(HugeIcons.Edit01, stringResource(R.string.local_llm_rename))
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(HugeIcons.Delete01, stringResource(R.string.local_llm_delete))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.local_llm_delete_confirm_title)) },
            text = { Text(stringResource(R.string.local_llm_delete_confirm_message, model.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.local_llm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
