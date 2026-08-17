package me.rerere.rikkahub.ui.pages.models.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ProviderSetting
import me.rerere.locallm.SdCatalogEntry
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.RECOMMENDED_PROVIDERS
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.modelmanager.ModelManagerViewModel
import me.rerere.rikkahub.ui.pages.modelmanager.Progress
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToModelsSheet(
    viewModel: ModelManagerViewModel = koinViewModel<ModelManagerViewModel>(),
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importModelFromUri(it) } }
    val editState = useEditState<ProviderSetting> { provider ->
        viewModel.addProvider(provider)
    }
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 16.dp)) {
            AddModelSectionHeader(
                title = stringResource(R.string.models_add_on_device),
                subtitle = stringResource(R.string.models_add_on_device_subtitle),
            )
            AddModelOptions(
                viewModel = viewModel,
                filePickerLauncher = filePickerLauncher,
                downloadProgress = downloadProgress,
                errorMessage = errorMessage,
            )

            AddModelSectionHeader(
                title = stringResource(R.string.models_add_connect_source),
                subtitle = stringResource(R.string.models_add_connect_source_subtitle),
            )
            RECOMMENDED_PROVIDERS.forEach { provider ->
                TextButton(
                    onClick = { editState.open(provider) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text(provider.name)
                }
            }
        }
    }

    if (editState.isEditing) {
        AlertDialog(
            onDismissRequest = editState::dismiss,
            title = { Text(stringResource(R.string.setting_provider_page_add_provider)) },
            text = {
                editState.currentState?.let { provider ->
                    ProviderConfigure(provider) { newState ->
                        editState.currentState = newState
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = editState::confirm) {
                    Text(stringResource(R.string.setting_provider_page_add))
                }
            },
            dismissButton = {
                TextButton(onClick = editState::dismiss) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ColumnScope.AddModelOptions(
    viewModel: ModelManagerViewModel,
    filePickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    downloadProgress: Progress?,
    errorMessage: String?,
) {
    val installedModels by viewModel.provider.collectAsStateWithLifecycle()
    val installedFiles = installedModels?.models?.map { it.modelId }?.toSet() ?: emptySet()

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            AddModelSectionHeader(
                title = stringResource(R.string.model_manager_add_section_catalog),
                subtitle = stringResource(R.string.model_manager_sd_catalog_subtitle),
            )
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

        item {
            AddModelSectionHeader(stringResource(R.string.model_manager_add_section_url))
        }
        item {
            HfUrlTab(viewModel, downloadProgress != null)
        }

        item {
            AddModelSectionHeader(stringResource(R.string.model_manager_add_section_file))
        }
        item {
            LocalImportTab(filePickerLauncher, downloadProgress != null)
        }
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

@Composable
private fun AddModelSectionHeader(
    title: String,
    subtitle: String? = null,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
