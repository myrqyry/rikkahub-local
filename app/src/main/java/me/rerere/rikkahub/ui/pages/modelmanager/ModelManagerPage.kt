package me.rerere.rikkahub.ui.pages.modelmanager

import android.content.Intent
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.models.ModelTab
import me.rerere.rikkahub.ui.pages.models.UnifiedModelsViewModel
import me.rerere.rikkahub.ui.pages.models.components.ModelInventorySection
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

private val MANAGER_TABS = ModelTab.entries.filter { it != ModelTab.EMBEDDINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerPage(
    // Koin registers this VM parameterised ({ params -> ... }) even though it takes no
    // parameters, so the type-less koinViewModel() overload can't match a definition and
    // the page crashed on open. The explicit type parameter picks the parameterised one.
    viewModel: ModelManagerViewModel = koinViewModel<ModelManagerViewModel>(),
) {
    val settingsVm: SettingVM = koinViewModel()
    val assignmentsVm: UnifiedModelsViewModel = koinViewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val visibleModels by assignmentsVm.managerVisibleModels.collectAsStateWithLifecycle()
    val providers by assignmentsVm.registryProviders.collectAsStateWithLifecycle()
    val selectedTab by assignmentsVm.selectedTab.collectAsStateWithLifecycle()
    val search by assignmentsVm.searchText.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importModelFromUri(uri)
    }
    var showAddModel by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.model_manager_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        bottomBar = {
            if (showAddModel) {
                OutlinedButton(
                    onClick = { showAddModel = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.model_manager_back_to_models))
                }
            } else {
                Button(
                    onClick = { showAddModel = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.model_manager_add_model))
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (showAddModel) {
                AddModelOptions(viewModel, filePickerLauncher, downloadProgress, errorMessage)
            } else {
                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal.coerceAtMost(MANAGER_TABS.lastIndex)) {
                    MANAGER_TABS.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { assignmentsVm.setTab(tab) },
                            text = { Text(tab.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = assignmentsVm::setSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.unified_models_search)) },
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ModelInventorySection(
                            models = visibleModels,
                            providers = providers,
                            onRefreshProvider = assignmentsVm::refreshProvider,
                            onProviderEnabledChange = { providerId, enabled ->
                                val providerUuid = runCatching { Uuid.parse(providerId) }.getOrNull()
                                if (providerUuid != null) {
                                    settingsVm.updateSettings(settings.copy(
                                        providers = settings.providers.map { provider ->
                                            if (provider.id == providerUuid) provider.copyProvider(enabled = enabled) else provider
                                        },
                                    ))
                                }
                            },
                            onModelEnabledChange = assignmentsVm::setModelEnabled,
                            onCloudModelClick = { model ->
                                val providerId = (model.source as? ModelSource.Cloud)?.providerId
                                if (providerId != null) {
                                    navController.navigate(Screen.SettingProviderDetail(providerId))
                                }
                            },
                            onProviderConfigure = { providerId ->
                                navController.navigate(Screen.SettingProviderDetail(providerId))
                            },
                        )
                    }
                }
            }
        }
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
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            AddModelSectionHeader(stringResource(R.string.model_manager_add_section_installed))
        }
        val installed = installedModels?.models ?: emptyList()
        if (installed.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.model_manager_no_models),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            items(installed, key = { it.modelId }) { model ->
                InstalledModelRow(
                    model = model,
                    onRename = { newName -> viewModel.renameModel(model.modelId, newName) },
                    onDelete = { viewModel.deleteModel(model.modelId) },
                )
            }
        }

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
