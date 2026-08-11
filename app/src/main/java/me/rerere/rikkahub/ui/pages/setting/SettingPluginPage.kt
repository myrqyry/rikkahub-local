package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.Package
import me.rerere.rikkahub.R
import me.rerere.rikkahub.skills.imports.ImportCandidate
import me.rerere.rikkahub.skills.imports.ArtifactKind
import me.rerere.rikkahub.skills.imports.ImportCoordinator
import me.rerere.rikkahub.skills.imports.ImportResult
import me.rerere.rikkahub.skills.plugins.PluginManager
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingPluginPage() {
    val manager: PluginManager = koinInject()
    val coordinator: ImportCoordinator = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var installUrl by remember { mutableStateOf("") }
    var installError by remember { mutableStateOf<String?>(null) }
    var pendingInstall by remember { mutableStateOf<ImportCandidate?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    val plugins by manager.installedPlugins.collectAsStateWithLifecycle()
    val pendingOwnership = remember { CandidateOwnership(coordinator::discard) }

    DisposableEffect(Unit) {
        onDispose { pendingOwnership.close() }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_plugin_page_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = {
                        context.openUrl("https://github.com/anthropics/claude-code/tree/main/plugins")
                    }) {
                        Icon(
                            HugeIcons.Earth,
                            contentDescription = stringResource(R.string.setting_home_browse_catalogs),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("install") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = CustomColors.listItemColors.containerColor,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = installUrl,
                                onValueChange = { installUrl = it; installError = null },
                                label = { Text(stringResource(R.string.setting_plugin_reference)) },
                                placeholder = { Text("telegram@claude-plugins-official") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                isError = installError != null,
                                supportingText = installError?.let { { Text(it) } },
                            )
                            IconButton(onClick = {
                                val url = installUrl.trim()
                                 if (url.isBlank() || preparing || installing) return@IconButton
                                 preparing = true
                                 installError = null
                                 scope.launch {
                                     coordinator.prepare(url, ArtifactKind.PLUGIN)
                                          .onSuccess {
                                              pendingOwnership.adopt(it)
                                              pendingInstall = it
                                          }
                                         .onFailure { installError = it.message }
                                     preparing = false
                                 }
                            }) {
                                Icon(HugeIcons.Add01, stringResource(R.string.setting_plugin_install))
                            }
                        }
                    }
                }
            }

            if (plugins.isEmpty()) {
                item("empty") {
                    Text(
                        text = stringResource(R.string.setting_plugin_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            items(plugins, key = { it.name }) { plugin ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = CustomColors.listItemColors.containerColor,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            HugeIcons.Package,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = plugin.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = plugin.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                            Text(
                                text = "v${plugin.version} by ${plugin.author}" +
                                        if (plugin.hasCommands) " • /${plugin.name}:* commands" else "" +
                                        if (plugin.hasTools) " • MCP tools" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch { manager.uninstall(plugin.name) }
                        }) {
                            Icon(HugeIcons.Delete02, stringResource(R.string.setting_plugin_uninstall), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    pendingInstall?.let { candidate ->
        ArtifactImportReviewDialog(
            candidate = candidate,
            details = stringResource(R.string.setting_plugin_import_details),
            onDismiss = {
                pendingOwnership.discard()
                pendingInstall = null
            },
            onConfirm = {
                pendingOwnership.take()
                pendingInstall = null
                installing = true
                scope.launch {
                    coordinator.install(candidate)
                        .onSuccess { result ->
                            withContext(Dispatchers.IO) { manager.refreshInventory() }
                            installUrl = ""
                            val installed = result as ImportResult.Installed
                            installError = buildString {
                                append(context.getString(R.string.setting_plugin_installed, installed.name))
                                installed.warning?.let { append(" — $it") }
                            }
                        }
                        .onFailure { installError = it.message }
                    installing = false
                }
            },
        )
    }
}

private class CandidateOwnership(private val discard: (ImportCandidate) -> Unit) {
    private var owned: ImportCandidate? = null
    private var closed = false

    fun adopt(candidate: ImportCandidate) {
        if (closed) {
            discard(candidate)
            return
        }
        owned?.let(discard)
        owned = candidate
    }

    fun take(): ImportCandidate? = owned.also { owned = null }

    fun discard() {
        take()?.let(discard)
    }

    fun close() {
        closed = true
        discard()
    }
}
