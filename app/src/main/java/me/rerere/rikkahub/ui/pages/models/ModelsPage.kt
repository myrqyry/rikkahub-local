package me.rerere.rikkahub.ui.pages.models

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
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
import me.rerere.rikkahub.data.modelregistry.capability
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.models.components.AddToModelsSheet
import me.rerere.rikkahub.ui.pages.models.components.ModelAssignmentsSection
import me.rerere.rikkahub.ui.pages.models.components.ModelInventorySection
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
            onAssignTitle = vm::assignTitle,
            onAssignTranslation = vm::assignTranslation,
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

    var query by rememberSaveable { mutableStateOf(request.search) }
    var filter by rememberSaveable { mutableStateOf(request.tab.toModelsFilter()) }
    var searchExpanded by rememberSaveable { mutableStateOf(request.search.isNotBlank()) }
    var showAddSheet by remember { mutableStateOf(false) }

    LaunchedEffect(request) {
        filter = request.tab.toModelsFilter()
        query = request.search
        if (request.search.isNotBlank()) searchExpanded = true
    }

    val visible = managerVisibleModels
        .filter { filter == ModelsFilter.ALL || filter.matches(it) }
        .filter { searchMatches(it, query) }
    val summaryRows = defaultAssignmentsSummary(assignments, managerVisibleModels)
    val searching = query.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.models_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft01, stringResource(R.string.back))
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
                                HugeIcons.Cancel01
                            } else {
                                HugeIcons.Search01
                            },
                            contentDescription = stringResource(R.string.unified_models_search),
                        )
                    }
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(HugeIcons.Add01, stringResource(R.string.models_add))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (searchExpanded) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.unified_models_search)) },
                        leadingIcon = { Icon(HugeIcons.Search01, null) },
                        singleLine = true,
                        shape = CircleShape,
                    )
                }
            }

            item {
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

            if (!searching && summaryRows.isNotEmpty()) {
                item {
                    DefaultAssignmentsStatus(
                        rows = summaryRows,
                        onClick = { navController.navigate(Screen.SettingDefaultModels) },
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.models_your_models),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            item {
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.models_used_by_default)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(HugeIcons.ArrowLeft01, stringResource(R.string.back))
                    }
                },
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.models_used_by_default),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(rows, key = { it.role.name }) { row ->
                val model = row.model ?: return@items
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = row.role.capability().icon,
                        contentDescription = stringResource(row.role.labelRes()),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
