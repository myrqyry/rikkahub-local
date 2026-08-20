package me.rerere.rikkahub.ui.pages.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.data.modelregistry.capability
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.models.components.SourceBadge
import me.rerere.rikkahub.ui.pages.models.components.sourceDisplayName
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDetailPage(
    modelId: String,
    vm: UnifiedModelsViewModel = koinViewModel(),
) {
    val navController = LocalNavController.current
    val allModels by vm.allModels.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val model = allModels.firstOrNull { it.id == modelId }
    var renaming by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf(false) }

    if (model == null) {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.models_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(HugeIcons.ArrowLeft01, stringResource(R.string.back))
                        }
                    },
                )
            },
        ) {
            Text(
                stringResource(R.string.unified_models_empty),
                modifier = Modifier.padding(it),
            )
        }
        return
    }

    val usedFor = defaultAssignmentsSummary(assignments, allModels)
        .filter { it.model?.id == model.id }
        .map { it.role }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(model.displayName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft01, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (model.source is ModelSource.Local) {
                        IconButton(onClick = {
                            renaming = true
                            renameText = model.displayName
                        }) {
                            Icon(HugeIcons.Edit01, stringResource(R.string.local_llm_rename))
                        }
                        IconButton(onClick = { pendingDelete = true }) {
                            Icon(HugeIcons.Delete01, stringResource(R.string.local_llm_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CardGroup(title = { Text(stringResource(R.string.models_source_section)) }) {
                    item(
                        headlineContent = { Text(sourceDisplayName(model)) },
                        supportingContent = { SourceBadge(model) },
                    )
                }
            }

            item {
                CardGroup(title = { Text(stringResource(R.string.models_capabilities)) }) {
                    model.capabilities.sortedBy { it.name }.forEach { cap ->
                        item(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = cap.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        stringResource(cap.labelRes),
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            },
                            trailingContent = {
                                Switch(
                                    checked = cap in model.enabledCapabilities,
                                    onCheckedChange = { enabled ->
                                        vm.setCapabilityEnabled(model.id, cap, enabled)
                                    },
                                )
                            },
                        )
                    }
                }
            }

            if (usedFor.isNotEmpty()) {
                item {
                    CardGroup(title = { Text(stringResource(R.string.models_used_for)) }) {
                        usedFor.forEach { role ->
                            item(headlineContent = { Text(stringResource(role.capability().labelRes)) })
                        }
                    }
                }
            }

            item {
                CardGroup(title = { Text(stringResource(R.string.models_status)) }) {
                    item(headlineContent = { Text(stringResource(model.lifecycle.labelRes)) })
                }
            }

            when (val source = model.source) {
                is ModelSource.Local -> {
                    item {
                        CardGroup(title = { Text(stringResource(R.string.models_local_details)) }) {
                            model.metadata["sizeBytes"]?.toLongOrNull()?.let { size ->
                                item(
                                    headlineContent = { Text(stringResource(R.string.models_storage)) },
                                    supportingContent = { Text(formatBytes(size)) },
                                )
                            }
                            item(
                                headlineContent = { Text(stringResource(R.string.models_runtime)) },
                                supportingContent = { Text(source.runtime.displayName) },
                            )
                            model.metadata["path"]?.let { path ->
                                item(headlineContent = { Text(path) })
                            }
                        }
                    }
                }
                is ModelSource.Cloud -> {
                    item {
                        CardGroup(title = { Text(stringResource(R.string.models_source_details)) }) {
                            item(headlineContent = { Text(sourceDisplayName(model)) })
                            item(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.models_remote_id),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                            item(headlineContent = { Text(source.remoteModelId) })
                            item(
                                headlineContent = {
                                    Text(
                                        stringResource(
                                            if (model.connected) R.string.models_connection_healthy
                                            else R.string.models_connection_unavailable,
                                        ),
                                    )
                                },
                            )
                            item(
                                headlineContent = { Text(stringResource(R.string.models_provider_settings)) },
                                onClick = { navController.navigate(Screen.SettingProviderDetail(source.providerId)) },
                                trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                            )
                        }
                    }
                }
            }

            item {
                CardGroup(title = { Text(stringResource(R.string.models_advanced)) }) {
                    item(headlineContent = { Text(stringResource(R.string.setting_provider_page_model_id)) })
                    item(headlineContent = { Text(model.id) })
                }
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
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
                        vm.renameLocalModel(model.id, renameText)
                        renaming = false
                    },
                    enabled = renameText.isNotBlank() && renameText != model.displayName,
                ) {
                    Text(stringResource(R.string.local_llm_rename_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text(stringResource(R.string.local_llm_delete_confirm_title)) },
            text = { Text(stringResource(R.string.local_llm_delete_confirm_message, model.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteLocalModel(model.id)
                    pendingDelete = false
                    navController.popBackStack()
                }) {
                    Text(stringResource(R.string.local_llm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.2f MB".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1L shl 10 -> "%.2f KB".format(bytes.toDouble() / (1L shl 10))
    else -> "$bytes B"
}
