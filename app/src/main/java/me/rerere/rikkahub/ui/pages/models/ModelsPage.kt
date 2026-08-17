package me.rerere.rikkahub.ui.pages.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.models.components.AddToModelsSheet
import me.rerere.rikkahub.ui.pages.models.components.ManageSourcesSheet
import me.rerere.rikkahub.ui.pages.models.components.ModelAssignmentsSection
import me.rerere.rikkahub.ui.pages.models.components.ModelInventorySection
import me.rerere.rikkahub.ui.pages.models.components.SourceBadge
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsPage(
    request: ModelManagerRequest = ModelManagerRequest(),
    showAssignments: Boolean = false,
    scrollToSources: Boolean = false,
    vm: UnifiedModelsViewModel = koinViewModel(),
    settingsVm: SettingVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val managerVisibleModels by vm.managerVisibleModels.collectAsState()
    val providers by vm.registryProviders.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val legacyAssignments by vm.legacyAssignments.collectAsState()
    val settings by settingsVm.settings.collectAsState()

    var query by rememberSaveable { mutableStateOf(request.search) }
    var filter by rememberSaveable { mutableStateOf(request.tab.toModelsFilter()) }
    var expandedAssignments by rememberSaveable { mutableStateOf(showAssignments) }
    var localOnly by rememberSaveable { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showManageSources by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(request) {
        filter = request.tab.toModelsFilter()
        query = request.search
    }
    LaunchedEffect(scrollToSources) {
        if (scrollToSources) listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }

    val visible = managerVisibleModels
        .filter { filter.matches(it) || filter == ModelsFilter.ALL }
        .filter { searchMatches(it, query) }
        .filter { !localOnly || it.source is ModelSource.Local }

    val summaryRows = defaultAssignmentsSummary(assignments, managerVisibleModels)
    val searching = query.isNotBlank()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.models_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft01, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(HugeIcons.Add01, stringResource(R.string.models_add))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.unified_models_search)) },
                    leadingIcon = { Icon(HugeIcons.Search01, null) },
                    trailingIcon = if (query.isNotBlank()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(HugeIcons.Cancel01, null)
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = CircleShape,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelsFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(stringResource(f.labelRes())) },
                        )
                    }
                }
            }

            if (!searching) {
                item {
                    Text(
                        stringResource(R.string.models_used_by_default),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (summaryRows.isNotEmpty()) {
                    item {
                        CardGroup {
                            summaryRows.forEach { row ->
                                val model = row.model ?: return@forEach
                                item(
                                    onClick = { navController.navigate(Screen.ModelDetail(model.id)) },
                                    headlineContent = { Text(stringResource(row.role.labelRes())) },
                                    supportingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(model.displayName)
                                            SourceBadge(model = model, modifier = Modifier.padding(start = 8.dp))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = { expandedAssignments = !expandedAssignments }) {
                        Text(
                            stringResource(
                                if (expandedAssignments) R.string.models_hide_assignments
                                else R.string.models_show_all_assignments,
                            ),
                        )
                    }
                }
                if (expandedAssignments) {
                    item {
                        ModelAssignmentsSection(
                            assignments = assignments,
                            legacyAssignments = legacyAssignments,
                            models = managerVisibleModels,
                            repairState = null,
                            onAssign = { role, id -> vm.assign(role, id) },
                            onAssignTitle = { vm.assignTitle(it) },
                            onAssignTranslation = { vm.assignTranslation(it) },
                            fastModelId = settings.fastModelId.toString(),
                            suggestionModelId = settings.suggestionModelId?.toString(),
                            compressModelId = settings.compressModelId.toString(),
                            enableSuggestion = settings.enableSuggestion,
                            onSuggestionEnabledChange = { settingsVm.updateSettings(settings.copy(enableSuggestion = it)) },
                            onFastModelSelected = { id -> settingsVm.updateSettings(settings.copy(fastModelId = Uuid.parse(id))) },
                            onSuggestionModelSelected = { id -> settingsVm.updateSettings(settings.copy(suggestionModelId = id?.let(Uuid::parse))) },
                            onCompressModelSelected = { id -> settingsVm.updateSettings(settings.copy(compressModelId = Uuid.parse(id))) },
                        )
                    }
                }
            }

            item {
                Text(stringResource(R.string.models_your_models), style = MaterialTheme.typography.titleSmall)
            }
            item {
                ModelInventorySection(
                    models = visible,
                    onModelEnabledChange = { model, enabled -> vm.setModelEnabled(model, enabled) },
                    onModelClick = { model -> navController.navigate(Screen.ModelDetail(model.id)) },
                )
            }

            if (!searching) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.models_sources), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { showManageSources = true }) {
                            Text(stringResource(R.string.models_manage_sources))
                        }
                    }
                }
                val localCount = managerVisibleModels.count { it.source is ModelSource.Local }
                item {
                    CardGroup {
                        item(
                            onClick = { localOnly = !localOnly },
                            headlineContent = { Text(stringResource(R.string.models_on_this_device)) },
                            supportingContent = { Text(stringResource(R.string.unified_models_provider_count, localCount)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        providers.forEach { provider ->
                            item(
                                onClick = { navController.navigate(Screen.SettingProviderDetail(provider.id)) },
                                headlineContent = { Text(provider.displayName) },
                                supportingContent = {
                                    Text(
                                        when {
                                            !provider.enabled -> stringResource(R.string.models_source_disabled)
                                            provider.modelIds.isEmpty() -> stringResource(R.string.models_source_not_configured)
                                            else -> stringResource(
                                                R.string.models_source_configured_count,
                                                provider.modelIds.size,
                                            )
                                        },
                                    )
                                },
                                trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddToModelsSheet(onDismiss = { showAddSheet = false })
    }
    if (showManageSources) {
        ManageSourcesSheet(onDismiss = { showManageSources = false })
    }
}

private fun ModelsFilter.labelRes(): Int = when (this) {
    ModelsFilter.ALL -> R.string.models_filter_all
    ModelsFilter.CHAT -> R.string.models_filter_chat
    ModelsFilter.VISION -> R.string.models_filter_vision
    ModelsFilter.IMAGE -> R.string.models_filter_image
    ModelsFilter.AUDIO -> R.string.models_filter_audio
    ModelsFilter.RETRIEVAL -> R.string.models_filter_retrieval
}

private fun ModelRole.labelRes(): Int = when (this) {
    ModelRole.CHAT -> R.string.models_role_chat
    ModelRole.VISION -> R.string.models_role_vision
    ModelRole.OCR -> R.string.models_role_ocr
    ModelRole.IMAGE_GENERATION -> R.string.models_role_image_generation
    ModelRole.IMAGE_EDITING -> R.string.models_role_image_editing
    ModelRole.TEXT_TO_SPEECH -> R.string.models_role_text_to_speech
    ModelRole.SPEECH_TO_TEXT -> R.string.models_role_speech_to_text
    ModelRole.EMBEDDINGS -> R.string.models_role_embeddings
}
