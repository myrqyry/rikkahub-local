package me.rerere.rikkahub.ui.pages.models.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelProviderDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.ui.components.ui.CardGroup

@Composable
fun ModelInventorySection(
    models: List<ModelDescriptor>,
    providers: List<ModelProviderDescriptor>,
    onRefreshProvider: (String) -> Unit,
    onProviderEnabledChange: (String, Boolean) -> Unit,
    onModelEnabledChange: (ModelDescriptor, Boolean) -> Unit,
    onLocalModelClick: (ModelDescriptor) -> Unit = {},
    onCloudModelClick: (ModelDescriptor) -> Unit = {},
    onProviderConfigure: (String) -> Unit = {},
    onLocalModelRename: (ModelDescriptor, String) -> Unit = { _, _ -> },
    onLocalModelDelete: (ModelDescriptor) -> Unit = {},
) {
    val local = models.filter { it.source is ModelSource.Local }
    val cloud = models.filter { it.source is ModelSource.Cloud }
    val cloudGroups = cloud.groupBy { (it.source as ModelSource.Cloud).providerId }
    var expandedProviders by remember(cloudGroups.keys) { mutableStateOf(cloudGroups.keys.toSet()) }
    var renamingModel by remember { mutableStateOf<ModelDescriptor?>(null) }
    var renameText by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ModelDescriptor?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (local.isNotEmpty()) {
            CardGroup(title = { Text(stringResource(R.string.unified_models_local)) }) {
                local.forEach { model ->
                    item(
                        onClick = { onLocalModelClick(model) },
                        headlineContent = { Text(model.displayName) },
                        supportingContent = { Text(model.capabilities.joinToString { it.name.lowercase() }) },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = {
                                        renamingModel = model
                                        renameText = model.displayName
                                    },
                                ) {
                                    Icon(HugeIcons.Edit01, stringResource(R.string.local_llm_rename))
                                }
                                IconButton(onClick = { pendingDelete = model }) {
                                    Icon(HugeIcons.Delete01, stringResource(R.string.local_llm_delete))
                                }
                                Switch(
                                    checked = model.enabledCapabilities.isNotEmpty(),
                                    onCheckedChange = { onModelEnabledChange(model, it) },
                                )
                            }
                        },
                    )
                }
            }
        }
        if (cloud.isNotEmpty()) {
            CardGroup(title = { Text(stringResource(R.string.unified_models_cloud)) }) {
                cloudGroups.forEach { (providerId, providerModels) ->
                    val provider = providers.firstOrNull { it.id == providerId }
                    item(
                        onClick = {
                            expandedProviders = if (providerId in expandedProviders) {
                                expandedProviders - providerId
                            } else {
                                expandedProviders + providerId
                            }
                        },
                        overlineContent = { Text(provider?.displayName ?: providerId) },
                        headlineContent = {
                            Text(
                                stringResource(
                                    R.string.unified_models_provider_count,
                                    providerModels.size,
                                ),
                            )
                        },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { onProviderConfigure(providerId) }) {
                                    Text(stringResource(R.string.unified_models_configure))
                                }
                                TextButton(onClick = { onRefreshProvider(providerId) }) {
                                    Text(stringResource(R.string.unified_models_refresh))
                                }
                                if (provider != null) {
                                    Switch(
                                        checked = provider.enabled,
                                        onCheckedChange = { onProviderEnabledChange(providerId, it) },
                                    )
                                }
                            }
                        },
                    )
                    if (providerId in expandedProviders) {
                        providerModels.forEach { model ->
                            item(
                                onClick = { onCloudModelClick(model) },
                                headlineContent = { Text(model.displayName) },
                                supportingContent = { Text(model.capabilities.joinToString { it.name.lowercase() }) },
                                trailingContent = {
                                    Switch(
                                        checked = model.enabledCapabilities.isNotEmpty(),
                                        onCheckedChange = { onModelEnabledChange(model, it) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        if (local.isEmpty() && cloud.isEmpty()) {
            Text(stringResource(R.string.unified_models_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    renamingModel?.let { model ->
        AlertDialog(
            onDismissRequest = { renamingModel = null },
            title = { Text(stringResource(R.string.local_llm_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.local_llm_rename_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLocalModelRename(model, renameText)
                        renamingModel = null
                    },
                    enabled = renameText.isNotBlank() && renameText != model.displayName,
                ) {
                    Text(stringResource(R.string.local_llm_rename_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingModel = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.local_llm_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.local_llm_delete_confirm_message, model.displayName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLocalModelDelete(model)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.local_llm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
