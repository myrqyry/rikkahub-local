package me.rerere.rikkahub.ui.pages.models.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.uuid.Uuid
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.modelmanager.ModelManagerViewModel
import me.rerere.rikkahub.ui.pages.modelmanager.Progress
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import org.koin.androidx.compose.koinViewModel

enum class AddModelsMode {
    ROOT,
    PROVIDER,
    HUGGING_FACE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToModelsSheet(
    viewModel: ModelManagerViewModel = koinViewModel(),
    settingsVm: SettingVM = koinViewModel(),
    onDismiss: () -> Unit,
) {
    val navController = LocalNavController.current
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importModelFromUri(it) } }
    val fluxFolderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { viewModel.importFluxPackageFromTree(it) } }
    val editState = useEditState<ProviderSetting> { provider ->
        viewModel.addProvider(provider)
    }
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(AddModelsMode.ROOT) }

    val remoteProviders = remember(settings.providers) {
        settings.providers.filterNot(::isLocalProvider)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (mode) {
                AddModelsMode.ROOT -> {
                    Text(
                        text = stringResource(R.string.models_add),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    AddChoiceRow(
                        title = stringResource(R.string.models_add_provider_api),
                        subtitle = stringResource(R.string.models_add_provider_api_desc),
                        onClick = { mode = AddModelsMode.PROVIDER },
                    )
                    AddChoiceRow(
                        title = stringResource(R.string.models_add_hugging_face),
                        subtitle = stringResource(R.string.models_add_hugging_face_desc),
                        onClick = { mode = AddModelsMode.HUGGING_FACE },
                    )
                    AddChoiceRow(
                        title = stringResource(R.string.models_add_local_file),
                        subtitle = stringResource(R.string.models_add_local_file_desc),
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    )
                    AddChoiceRow(
                        title = stringResource(R.string.model_manager_flux_import),
                        subtitle = stringResource(R.string.model_manager_flux_catalog_subtitle),
                        onClick = { fluxFolderPickerLauncher.launch(null) },
                    )
                }

                AddModelsMode.PROVIDER -> {
                    SheetSubpageHeader(
                        title = stringResource(R.string.models_add_provider_list),
                        onBack = { mode = AddModelsMode.ROOT },
                    )
                    LazyColumn {
                        item {
                            AddChoiceRow(
                                title = stringResource(R.string.models_add_custom_openai),
                                subtitle = stringResource(R.string.models_add_provider_api_desc),
                                onClick = {
                                    editState.open(
                                        ProviderSetting.OpenAI(
                                            id = Uuid.random(),
                                            name = "Custom API",
                                            baseUrl = "",
                                            apiKey = "",
                                            enabled = true,
                                            builtIn = false,
                                        )
                                    )
                                },
                            )
                        }
                        items(remoteProviders, key = { it.id }) { provider ->
                            AddChoiceRow(
                                title = provider.name,
                                subtitle = provider.models.takeIf { it.isNotEmpty() }?.let {
                                    "${it.size} model${if (it.size == 1) "" else "s"}"
                                },
                                onClick = {
                                    onDismiss()
                                    navController.navigate(
                                        Screen.SettingProviderDetail(provider.id.toString())
                                    )
                                },
                            )
                        }
                    }
                }

                AddModelsMode.HUGGING_FACE -> {
                    SheetSubpageHeader(
                        title = stringResource(R.string.models_add_hugging_face),
                        onBack = { mode = AddModelsMode.ROOT },
                    )
                    HuggingFaceImport(
                        viewModel = viewModel,
                        downloadInProgress = downloadProgress != null,
                    )
                }
            }

            DownloadStatus(downloadProgress, errorMessage)
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

private fun isLocalProvider(provider: ProviderSetting): Boolean = when (provider) {
    is ProviderSetting.AICore,
    is ProviderSetting.LiteRtLocal,
    is ProviderSetting.StableDiffusion,
    is ProviderSetting.LlamaCppLocal -> true
    else -> false
}

@Composable
private fun AddChoiceRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = HugeIcons.ArrowRight01,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SheetSubpageHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(HugeIcons.ArrowLeft01, stringResource(R.string.back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HuggingFaceImport(
    viewModel: ModelManagerViewModel,
    downloadInProgress: Boolean,
) {
    var manualUrl by remember { mutableStateOf("") }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = manualUrl,
            onValueChange = { manualUrl = it },
            label = { Text(stringResource(R.string.local_llm_install_url_label)) },
            supportingText = { Text(stringResource(R.string.local_llm_install_url_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = {
                viewModel.startManualDownload(manualUrl)
                manualUrl = ""
            },
            enabled = manualUrl.isNotBlank() && !downloadInProgress,
        ) {
            Text(stringResource(R.string.local_llm_install_url_action))
        }
    }
}

@Composable
private fun DownloadStatus(
    downloadProgress: Progress?,
    errorMessage: String?,
) {
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

    errorMessage?.let { message ->
        Text(
            text = stringResource(R.string.local_llm_status_error_format, message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}
