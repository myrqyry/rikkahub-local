package me.rerere.rikkahub.ui.pages.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelProviderDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.models.components.ModelAssignmentsSection
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun UnifiedModelsPage(
    request: ModelsPageRequest = ModelsPageRequest(),
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val assignmentsVm: UnifiedModelsViewModel = koinViewModel()
    LaunchedEffect(request) {
        assignmentsVm.setTab(request.tab)
        assignmentsVm.setSearch(request.search)
        assignmentsVm.setSourceFilter(request.source)
        assignmentsVm.setProviderFilter(request.providerId)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_model_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        ModelSettingsPage(settings, vm, assignmentsVm, request, contentPadding)
    }
}

@Composable
private fun ModelSettingsPage(
    settings: me.rerere.rikkahub.data.datastore.Settings,
    vm: SettingVM,
    assignmentsVm: UnifiedModelsViewModel,
    request: ModelsPageRequest,
    contentPadding: PaddingValues,
) {
    val assignments by assignmentsVm.assignments.collectAsStateWithLifecycle()
    val legacyAssignments by assignmentsVm.legacyAssignments.collectAsStateWithLifecycle()
    val models by assignmentsVm.allModels.collectAsStateWithLifecycle()
    val repairState by assignmentsVm.repairState.collectAsStateWithLifecycle()
    val visibleModels by assignmentsVm.visibleModels.collectAsStateWithLifecycle()
    val selectedTab by assignmentsVm.selectedTab.collectAsStateWithLifecycle()
    val search by assignmentsVm.searchText.collectAsStateWithLifecycle()
    val providers by assignmentsVm.registryProviders.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(request.focus, request.modelId) {
        if (request.focus == ModelsFocus.MODELS || request.modelId != null) {
            listState.animateScrollToItem(1)
        } else if (request.focus == ModelsFocus.ASSIGNMENTS) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ModelAssignmentsSection(
                assignments = assignments,
                legacyAssignments = legacyAssignments,
                models = models,
                repairState = repairState,
                onAssign = assignmentsVm::assign,
                onAssignTitle = assignmentsVm::assignTitle,
                onAssignTranslation = assignmentsVm::assignTranslation,
                fastModelId = settings.fastModelId.toString(),
                suggestionModelId = settings.suggestionModelId?.toString(),
                compressModelId = settings.compressModelId.toString(),
                enableSuggestion = settings.enableSuggestion,
                onSuggestionEnabledChange = { enabled -> vm.updateSettings(settings.copy(enableSuggestion = enabled)) },
                onFastModelSelected = { id -> vm.updateSettings(settings.copy(fastModelId = Uuid.parse(id))) },
                onSuggestionModelSelected = { id -> vm.updateSettings(settings.copy(suggestionModelId = id?.let(Uuid::parse))) },
                onCompressModelSelected = { id -> vm.updateSettings(settings.copy(compressModelId = Uuid.parse(id))) },
            )
        }
        item {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                ModelTab.entries.forEach { tab ->
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
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.unified_models_search)) },
            )
        }
        item {
            ModelInventorySection(
                models = visibleModels,
                providers = providers,
                onRefreshProvider = assignmentsVm::refreshProvider,
                onProviderEnabledChange = { providerId, enabled ->
                    val providerUuid = runCatching { Uuid.parse(providerId) }.getOrNull()
                    if (providerUuid != null) {
                        vm.updateSettings(settings.copy(
                            providers = settings.providers.map { provider ->
                                if (provider.id == providerUuid) provider.copyProvider(enabled = enabled) else provider
                            },
                        ))
                    }
                },
                onModelEnabledChange = assignmentsVm::setModelEnabled,
            )
        }
    }
}

@Composable
private fun ModelInventorySection(
    models: List<ModelDescriptor>,
    providers: List<ModelProviderDescriptor>,
    onRefreshProvider: (String) -> Unit,
    onProviderEnabledChange: (String, Boolean) -> Unit,
    onModelEnabledChange: (ModelDescriptor, Boolean) -> Unit,
) {
    val navController = LocalNavController.current
    val local = models.filter { it.source is ModelSource.Local }
    val cloud = models.filter { it.source is ModelSource.Cloud }
    val cloudGroups = cloud.groupBy { (it.source as ModelSource.Cloud).providerId }
    var expandedProviders by remember(cloudGroups.keys) { mutableStateOf(cloudGroups.keys.toSet()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (local.isNotEmpty()) {
            CardGroup(title = { Text(stringResource(R.string.unified_models_local)) }) {
                local.forEach { model ->
                    item(
                        onClick = { navController.navigate(Screen.SettingModelManager) },
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
        if (cloud.isNotEmpty()) {
            CardGroup(title = { Text(stringResource(R.string.unified_models_cloud)) }) {
                cloudGroups.forEach { (providerId, providerModels) ->
                        item(
                            onClick = {
                                expandedProviders = if (providerId in expandedProviders) {
                                    expandedProviders - providerId
                                } else {
                                    expandedProviders + providerId
                                }
                            },
                            overlineContent = { Text(providerId) },
                            headlineContent = { Text(stringResource(R.string.unified_models_provider)) },
                            trailingContent = {
                                val provider = providers.firstOrNull { it.id == providerId }
                                Row {
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
                                    onClick = {
                                        navController.navigate(Screen.SettingProviderDetail(providerId))
                                    },
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
}
