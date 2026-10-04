package me.rerere.rikkahub.ui.pages.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import me.rerere.rikkahub.ui.icons.MingcuteIcons
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.capability
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.models.components.AddToModelsSheet
import me.rerere.rikkahub.ui.pages.models.components.ManageSourcesSheet
import me.rerere.rikkahub.ui.pages.models.components.ModelAssignmentsSection
import me.rerere.rikkahub.ui.pages.models.components.ModelInventorySection
import me.rerere.rikkahub.ui.pages.models.components.ProviderSummaryCard
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.theme.CustomColors
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

    if (showAssignments) {
        ModelAssignmentsPage(
            assignments = assignments,
            legacyAssignments = legacyAssignments,
            models = managerVisibleModels,
            fastModelId = settings.fastModelId.toString(),
            suggestionModelId = settings.suggestionModelId?.toString(),
            compressModelId = settings.compressModelId.toString(),
            enableSuggestion = settings.enableSuggestion,
            onBack = { navController.popBackStack() },
            onAssign = { role, id -> vm.assign(role, id) },
            onAssignTitle = { vm.assignTitle(it) },
            onAssignTranslation = { vm.assignTranslation(it) },
            onSuggestionEnabledChange = {
                settingsVm.updateSettings(settings.copy(enableSuggestion = it))
            },
            onFastModelSelected = { id ->
                settingsVm.updateSettings(settings.copy(fastModelId = Uuid.parse(id)))
            },
            onSuggestionModelSelected = { id ->
                settingsVm.updateSettings(settings.copy(suggestionModelId = id?.let(Uuid::parse)))
            },
            onCompressModelSelected = { id ->
                settingsVm.updateSettings(settings.copy(compressModelId = Uuid.parse(id)))
            },
        )
        return
    }

    val providerFocused = scrollToSources
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var query by rememberSaveable { mutableStateOf(request.search) }
    var filter by rememberSaveable { mutableStateOf(request.tab.toModelsFilter()) }
    var searchExpanded by rememberSaveable { mutableStateOf(request.search.isNotBlank()) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showManageSources by remember { mutableStateOf(false) }

    LaunchedEffect(request) {
        filter = request.tab.toModelsFilter()
        query = request.search
        if (request.search.isNotBlank()) searchExpanded = true
    }

    val visible = managerVisibleModels
        .filter { filter == ModelsFilter.ALL || filter.matches(it) }
        .filter { searchMatches(it, query) }
    val sourceProviders = remember(settings.providers, query) {
        settings.providers.filter { provider ->
            query.isBlank() || provider.name.contains(query, ignoreCase = true)
        }
    }
    val summaryRows = defaultAssignmentsSummary(assignments, managerVisibleModels)
    val searching = query.isNotBlank()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (providerFocused) R.string.setting_provider_page_title
                            else R.string.models_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(MingcuteIcons.ArrowLeft01, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (searchExpanded && query.isNotBlank()) {
                                query = ""
                            } else {
                                searchExpanded = !searchExpanded
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (searchExpanded && query.isNotBlank()) {
                                MingcuteIcons.Cancel01
                            } else {
                                MingcuteIcons.Search01
                            },
                            contentDescription = stringResource(
                                if (providerFocused) R.string.setting_provider_page_search_providers
                                else R.string.unified_models_search
                            ),
                        )
                    }
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(MingcuteIcons.Add01, stringResource(R.string.models_add))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (searchExpanded) {
                item("search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                stringResource(
                                    if (providerFocused) R.string.setting_provider_page_search_providers
                                    else R.string.unified_models_search
                                )
                            )
                        },
                        leadingIcon = { Icon(MingcuteIcons.Search01, null) },
                        singleLine = true,
                        shape = CircleShape,
                    )
                }
            }

            if (providerFocused) {
                item("sources-heading") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.models_sources),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        TextButton(onClick = { showManageSources = true }) {
                            Text(stringResource(R.string.models_manage_sources))
                        }
                    }
                }

                if (sourceProviders.isEmpty()) {
                    item("sources-empty") {
                        Text(
                            text = stringResource(R.string.model_list_no_providers),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(sourceProviders, key = { it.id }) { provider ->
                        ProviderSummaryCard(
                            provider = provider,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                navController.navigate(
                                    Screen.SettingProviderDetail(provider.id.toString())
                                )
                            },
                        )
                    }
                }
            } else {
                item("filters") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ModelsFilter.entries, key = { it.name }) { candidate ->
                            FilterChip(
                                selected = filter == candidate,
                                onClick = { filter = candidate },
                                label = { Text(stringResource(candidate.labelRes())) },
                            )
                        }
                    }
                }

                if (!searching) {
                    item("defaults") {
                        DefaultAssignmentsStatus(
                            rows = summaryRows,
                            onClick = { navController.navigate(Screen.SettingDefaultModels) },
                        )
                    }
                }

                item("models-heading") {
                    Text(
                        stringResource(R.string.models_your_models),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
                item("models-inventory") {
                    ModelInventorySection(
                        models = visible,
                        providers = providers,
                        onModelEnabledChange = { model, enabled -> vm.setModelEnabled(model, enabled) },
                        onModelClick = { model -> navController.navigate(Screen.ModelDetail(model.id)) },
                        onProviderClick = { provider ->
                            navController.navigate(Screen.SettingProviderDetail(provider.id))
                        },
                    )
                }
            }
        }
    }

    if (showManageSources) {
        ManageSourcesSheet(onDismiss = { showManageSources = false })
    }
    if (showAddSheet) {
        AddToModelsSheet(onDismiss = { showAddSheet = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelAssignmentsPage(
    assignments: me.rerere.rikkahub.data.modelregistry.ModelAssignments,
    legacyAssignments: LegacyAssignments,
    models: List<me.rerere.rikkahub.data.modelregistry.ModelDescriptor>,
    fastModelId: String,
    suggestionModelId: String?,
    compressModelId: String,
    enableSuggestion: Boolean,
    onBack: () -> Unit,
    onAssign: (ModelRole, String?) -> Unit,
    onAssignTitle: (String?) -> Unit,
    onAssignTranslation: (String?) -> Unit,
    onSuggestionEnabledChange: (Boolean) -> Unit,
    onFastModelSelected: (String) -> Unit,
    onSuggestionModelSelected: (String?) -> Unit,
    onCompressModelSelected: (String) -> Unit,
) {
    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.models_used_by_default)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MingcuteIcons.ArrowLeft01, stringResource(R.string.back))
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                ModelAssignmentsSection(
                    assignments = assignments,
                    legacyAssignments = legacyAssignments,
                    models = models,
                    repairState = null,
                    onAssign = onAssign,
                    onAssignTitle = onAssignTitle,
                    onAssignTranslation = onAssignTranslation,
                    fastModelId = fastModelId,
                    suggestionModelId = suggestionModelId,
                    compressModelId = compressModelId,
                    enableSuggestion = enableSuggestion,
                    onSuggestionEnabledChange = onSuggestionEnabledChange,
                    onFastModelSelected = onFastModelSelected,
                    onSuggestionModelSelected = onSuggestionModelSelected,
                    onCompressModelSelected = onCompressModelSelected,
                )
            }
        }
    }
}

@Composable
private fun DefaultAssignmentsStatus(
    rows: List<AssignmentSummaryRow>,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.models_used_by_default),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    imageVector = MingcuteIcons.ArrowRight01,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(rows, key = { it.role.name }) { row ->
                    row.model?.let { model ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    painter = painterResource(row.role.capability().iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(row.role.labelRes()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ModelsFilter.labelRes(): Int = when (this) {
    ModelsFilter.ALL -> R.string.models_filter_all
    ModelsFilter.CHAT -> R.string.models_filter_chat
    ModelsFilter.VISION -> R.string.models_filter_vision
    ModelsFilter.IMAGE -> R.string.models_filter_image
    ModelsFilter.AUDIO -> R.string.models_filter_audio
    ModelsFilter.EMBEDDINGS -> R.string.unified_models_tab_embeddings
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
