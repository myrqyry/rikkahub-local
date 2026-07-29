package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

private val BACKEND_TYPES = listOf("sd15npu", "sd15cpu", "sdxl", "anima")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingLocalDreamPage() {
    val settingsStore: SettingsStore = koinInject()
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
                title = { Text("Local Dream") },
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
                    title = { Text("Model") },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.AiMagic, null) },
                        headlineContent = { Text("Model ID") },
                        supportingContent = { Text(modelId) },
                    )
                }
            }

            item("fields") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("Parameters") },
                ) {
                    item(
                        headlineContent = {
                            OutlinedTextField(
                                value = modelId,
                                onValueChange = { modelId = it; save() },
                                label = { Text("Model ID") },
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
                                label = { Text("Width") },
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
                                label = { Text("Height") },
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
                                label = { Text("Steps") },
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
                                label = { Text("CFG Scale") },
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
                                    label = { Text("Backend Type") },
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
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }
            }
        }
    }
}