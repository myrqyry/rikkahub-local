package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Globe
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.opencode.OpenCodeWorkspaceRow
import me.rerere.rikkahub.data.opencode.OpenCodeProject
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun WorkspacePage(vm: WorkspaceVM = koinViewModel()) {
    val navController = LocalNavController.current
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val openCodeWorkspaces by vm.openCodeWorkspaces.collectAsStateWithLifecycle()
    val openCodeProjects by vm.openCodeProjects.collectAsStateWithLifecycle()
    val openCodeProjectError by vm.openCodeProjectError.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var showOpenCodeDialog by rememberSaveable { mutableStateOf(false) }
    var openCodeProjectConnectionId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.workspace_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(HugeIcons.Add01, contentDescription = null)
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (workspaces.isEmpty() && openCodeWorkspaces.isEmpty()) {
                item {
                    EmptyWorkspaceState()
                }
            }

            if (workspaces.isNotEmpty()) {
                item {
                    WorkspaceSectionHeader(stringResource(R.string.workspace_page_section_local))
                }
                items(workspaces, key = { it.id }) { workspace ->
                    WorkspaceCard(
                        workspace = workspace,
                        onRename = { editTarget = workspace },
                        onDelete = { deleteTarget = workspace },
                        onOpen = { navController.navigate(Screen.WorkspaceDetail(workspace.id)) },
                    )
                }
            }

            item {
                WorkspaceSectionHeader(stringResource(R.string.workspace_page_section_opencode))
            }
            if (openCodeWorkspaces.isNotEmpty()) {
                items(openCodeWorkspaces, key = { "opencode-${it.connection.id}-${it.reference?.id ?: "connection"}" }) { row ->
                     OpenCodeWorkspaceCard(
                          row = row,
                          onOpen = {
                              row.reference?.let { reference ->
                                  navController.navigate(Screen.OpenCodeWorkspaceDetail(reference.id))
                              } ?: run {
                                  openCodeProjectConnectionId = row.connection.id
                                  vm.discoverOpenCodeProjects(row.connection.id)
                              }
                          },
                     )
                }
            }

            item {
                TextButton(onClick = { showOpenCodeDialog = true }) {
                    Text(stringResource(R.string.workspace_page_add_opencode))
                }
            }
        }
    }

    if (showOpenCodeDialog) {
        OpenCodeConnectionDialog(
            onDismiss = { showOpenCodeDialog = false },
            onConfirm = { name, url, credential ->
                vm.createOpenCodeConnection(name, url, credential)
                showOpenCodeDialog = false
            },
        )
    }

    if (openCodeProjects.isNotEmpty() || openCodeProjectError != null) {
        OpenCodeProjectDialog(
            projects = openCodeProjects,
            error = openCodeProjectError,
            onSelect = { project -> openCodeProjectConnectionId?.let { vm.selectOpenCodeProject(it, project) } },
            onDismiss = {
                openCodeProjectConnectionId = null
                vm.clearOpenCodeProjects()
            },
        )
    }

    if (showAddDialog) {
        EditWorkspaceDialog(
            title = stringResource(R.string.workspace_page_create),
            initialName = "",
            existingNames = workspaces.map { it.name.trim() }.toSet(),
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                vm.create(name)
                showAddDialog = false
            },
        )
    }

    editTarget?.let { workspace ->
        EditWorkspaceDialog(
            title = stringResource(R.string.workspace_page_rename),
            initialName = workspace.name,
            existingNames = workspaces.filter { it.id != workspace.id }.map { it.name.trim() }.toSet(),
            onDismiss = { editTarget = null },
            onConfirm = { name ->
                vm.rename(workspace, name)
                editTarget = null
            },
        )
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.workspace_page_delete),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            deleteTarget?.let { vm.delete(it) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.workspace_page_delete_confirm))
    }
}

@Composable
private fun WorkspaceSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun OpenCodeWorkspaceCard(row: OpenCodeWorkspaceRow, onOpen: (() -> Unit)?) {
    val reference = row.reference
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = HugeIcons.Globe,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = reference?.name ?: row.connection.name,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = reference?.remoteDirectory ?: row.connection.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ConnectionStatusLabel(row.connection.lastHealthStatus)
        }
    }
}

@Composable
private fun ConnectionStatusLabel(status: String) {
    val (label, color) = when (status) {
        "HEALTHY" -> stringResource(R.string.workspace_opencode_status_online) to MaterialTheme.colorScheme.primary
        "UNHEALTHY" -> stringResource(R.string.workspace_opencode_status_unavailable) to MaterialTheme.colorScheme.tertiary
        "ERROR" -> stringResource(R.string.workspace_opencode_status_error) to MaterialTheme.colorScheme.error
        else -> stringResource(R.string.workspace_opencode_status_unknown) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
private fun OpenCodeProjectDialog(
    projects: List<OpenCodeProject>,
    error: String?,
    onSelect: (OpenCodeProject) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select OpenCode project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                projects.forEach { project ->
                    TextButton(onClick = { onSelect(project) }, modifier = Modifier.fillMaxWidth()) {
                        Text(project.name ?: project.directory, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (projects.isEmpty() && error == null) Text("No OpenCode projects found")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun OpenCodeConnectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var credential by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add OpenCode connection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Server URL") }, singleLine = true)
                OutlinedTextField(credential, { credential = it }, label = { Text("Credential (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), url.trim(), credential.takeIf { it.isNotBlank() }) },
                enabled = name.isNotBlank() && url.isNotBlank(),
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun EmptyWorkspaceState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = HugeIcons.File02,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.workspace_page_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.workspace_page_empty_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorkspaceCard(
    workspace: WorkspaceEntity,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = HugeIcons.File02,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = workspace.name,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = workspace.shellStatus.toShellStatusLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(HugeIcons.MoreVertical, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_rename)) },
                            leadingIcon = { Icon(HugeIcons.Edit01, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    imageVector = HugeIcons.Delete01,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditWorkspaceDialog(
    title: String,
    initialName: String,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val trimmedName = name.trim()
    val isDuplicate = trimmedName.isNotEmpty() && trimmedName in existingNames

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.workspace_page_name)) },
                singleLine = true,
                isError = isDuplicate,
                supportingText = if (isDuplicate) {
                    { Text(stringResource(R.string.workspace_page_name_duplicate)) }
                } else null,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedName) },
                enabled = name.isNotBlank() && !isDuplicate,
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
