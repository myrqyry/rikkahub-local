package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Download02
import me.rerere.hugeicons.stroke.Link02
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.plus
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

private val BACKEND_TYPES = listOf("sd15npu", "sd15cpu", "sdxl", "anima")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingLocalDreamPage() {
    val context = LocalContext.current
    val settingsStore: SettingsStore = koinInject()
    val httpClient: OkHttpClient = koinInject()
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val localDreamProvider = settings.providers.firstOrNull { it is ProviderSetting.LocalDream }
        as? ProviderSetting.LocalDream
    val initial = localDreamProvider ?: ProviderSetting.LocalDream()

    var modelId by remember(initial) { mutableStateOf(initial.modelId) }
    var width by remember(initial) { mutableStateOf(initial.width.toString()) }
    var height by remember(initial) { mutableStateOf(initial.height.toString()) }
    var steps by remember(initial) { mutableStateOf(initial.steps.toString()) }
    var cfg by remember(initial) { mutableStateOf(initial.cfg.toString()) }
    var backendType by remember(initial) { mutableStateOf(initial.backendType) }
    var port by remember(initial) { mutableStateOf(initial.port.toString()) }
    var backendExpanded by remember { mutableStateOf(false) }

    var availableModels by remember { mutableStateOf<List<LocalDreamModelDownloader.RemoteModel>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val downloadProgress = remember { mutableStateMapOf<String, LocalDreamModelDownloader.Progress>() }
    val downloadedModels = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        try {
            val models = LocalDreamModelDownloader.fetchModels(httpClient)
            availableModels = models
            models.forEach { m ->
                downloadedModels[m.fileName] = LocalDreamModelDownloader.isModelDownloaded(context, m)
            }
        } catch (_: Exception) {}
        isLoadingModels = false
    }

    fun save() {
        scope.launch {
            settingsStore.update { s ->
                s.copy(
                    providers = s.providers.map { p ->
                        if (p is ProviderSetting.LocalDream) {
                            p.copy(
                                modelId = modelId,
                                width = width.toIntOrNull() ?: p.width,
                                height = height.toIntOrNull() ?: p.height,
                                steps = steps.toIntOrNull() ?: p.steps,
                                cfg = cfg.toFloatOrNull() ?: p.cfg,
                                backendType = backendType,
                                port = port.toIntOrNull() ?: p.port,
                            )
                        } else p
                    }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_local_dream_page_title)) },
                navigationIcon = { BackButton() },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("model") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_local_dream_model)) },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.AiMagic, null) },
                        headlineContent = { Text(stringResource(R.string.setting_local_dream_model_id)) },
                        supportingContent = { Text(modelId) },
                    )
                }
            }

            item("fields") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_local_dream_parameters)) },
                ) {
                    item(
                        headlineContent = {
                            OutlinedTextField(
                                value = modelId,
                                onValueChange = { modelId = it; save() },
                                label = { Text(stringResource(R.string.setting_local_dream_model_id)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                    item(
                        headlineContent = {
                            OutlinedTextField(
                                value = width,
                                onValueChange = { width = it; it.toIntOrNull()?.let { save() } },
                                label = { Text(stringResource(R.string.setting_local_dream_width)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                    item(
                        headlineContent = {
                            OutlinedTextField(
                                value = height,
                                onValueChange = { height = it; it.toIntOrNull()?.let { save() } },
                                label = { Text(stringResource(R.string.setting_local_dream_height)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                    item(
                        headlineContent = {
                            OutlinedTextField(
                                value = steps,
                                onValueChange = { steps = it; it.toIntOrNull()?.let { save() } },
                                label = { Text(stringResource(R.string.setting_local_dream_steps)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                    item(
                        headlineContent = {
                            OutlinedTextField(
                                value = cfg,
                                onValueChange = { cfg = it; it.toFloatOrNull()?.let { save() } },
                                label = { Text(stringResource(R.string.setting_local_dream_cfg_scale)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                    item(
                        headlineContent = {
                            ExposedDropdownMenuBox(
                                expanded = backendExpanded,
                                onExpandedChange = { backendExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = backendType,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.setting_local_dream_backend_type)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = backendExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                )
                                ExposedDropdownMenu(
                                    expanded = backendExpanded,
                                    onDismissRequest = { backendExpanded = false },
                                ) {
                                    BACKEND_TYPES.forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type) },
                                            onClick = {
                                                backendType = type
                                                backendExpanded = false
                                                save()
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                    item(
                        headlineContent = {
                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it; it.toIntOrNull()?.let { save() } },
                                label = { Text(stringResource(R.string.setting_local_dream_port)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }
            }

            item("model_download_header") {
                Column {
                    Text(stringResource(R.string.setting_local_dream_download_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp))
                    TextButton(onClick = { context.openUrl("https://huggingface.co/xororz/sd-qnn/tree/main") }, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Icon(HugeIcons.Link02, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.setting_local_dream_browse_models), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (isLoadingModels) {
                item("loading") {
                    Text(stringResource(R.string.setting_local_dream_loading), modifier = Modifier.padding(horizontal = 12.dp))
                }
            } else if (availableModels.isEmpty()) {
                item("error") {
                    Text(stringResource(R.string.setting_local_dream_load_failed), modifier = Modifier.padding(horizontal = 12.dp))
                }
            } else {
                item("search") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.setting_local_dream_search)) },
                        singleLine = true,
                        leadingIcon = { Icon(HugeIcons.Search01, null) },
                        modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(),
                    )
                }

                val filtered = if (searchQuery.isBlank()) availableModels
                else availableModels.filter { it.modelName.contains(searchQuery, ignoreCase = true) }

                filtered.groupBy { it.modelName }.forEach { (name, variants) ->
                    item("model_$name") {
                        CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                            item(headlineContent = {
                                Column {
                                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    variants.forEach { model ->
                                        val state = downloadProgress[model.fileName]
                                        val done = state is LocalDreamModelDownloader.Progress.Done || downloadedModels[model.fileName] == true

                                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(model.tier, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(formatSize(model.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                when {
                                                    done -> {
                                                        OutlinedButton(onClick = { modelId = name; save() }) {
                                                            Icon(HugeIcons.CheckmarkCircle02, null, Modifier.size(16.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text(stringResource(R.string.setting_local_dream_use), style = MaterialTheme.typography.labelLarge)
                                                        }
                                                    }
                                                    state is LocalDreamModelDownloader.Progress.Failed -> {
                                                        Button(onClick = {
                                                            val dl = model
                                                            scope.launch { LocalDreamModelDownloader.downloadModel(context, httpClient, dl).collect { p -> downloadProgress[dl.fileName] = p; if (p is LocalDreamModelDownloader.Progress.Done) { downloadedModels[dl.fileName] = true; modelId = dl.modelName; save() } } }
                                                        }) { Text(stringResource(R.string.setting_local_dream_retry)) }
                                                    }
                                                    state != null -> {}
                                                    else -> {
                                                        Button(onClick = {
                                                            val dl = model
                                                            scope.launch { LocalDreamModelDownloader.downloadModel(context, httpClient, dl).collect { p -> downloadProgress[dl.fileName] = p; if (p is LocalDreamModelDownloader.Progress.Done) { downloadedModels[dl.fileName] = true; modelId = dl.modelName; save() } } }
                                                        }) {
                                                            Icon(HugeIcons.Download02, null, Modifier.size(16.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                             Text(stringResource(R.string.setting_local_dream_download), style = MaterialTheme.typography.labelLarge)
                                                        }
                                                    }
                                                }
                                            }
                                            when (state) {
                                                is LocalDreamModelDownloader.Progress.Started -> { Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.setting_local_dream_starting), style = MaterialTheme.typography.bodySmall) }
                                                is LocalDreamModelDownloader.Progress.Downloading -> {
                                                    Spacer(Modifier.height(4.dp))
                                                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                                                    Spacer(Modifier.height(2.dp))
                                                    Text("${state.percent}%", style = MaterialTheme.typography.bodySmall)
                                                }
                                                is LocalDreamModelDownloader.Progress.Extracting -> {
                                                    Spacer(Modifier.height(4.dp))
                                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                                    Spacer(Modifier.height(2.dp))
                                                     Text(stringResource(R.string.setting_local_dream_extracting), style = MaterialTheme.typography.bodySmall)
                                                }
                                                is LocalDreamModelDownloader.Progress.Failed -> Text(state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
