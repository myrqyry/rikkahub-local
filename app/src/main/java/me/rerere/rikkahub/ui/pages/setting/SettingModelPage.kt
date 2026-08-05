package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.AiEditing
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.modelregistry.LegacyModelAssignmentAdapter
import me.rerere.rikkahub.data.modelregistry.ModelRegistry
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.pages.models.UnifiedModelsViewModel
import me.rerere.rikkahub.ui.pages.models.components.ModelAssignmentsSection
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun SettingModelPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val registry: ModelRegistry = koinInject()
    val legacyAdapter: LegacyModelAssignmentAdapter = koinInject()
    val assignmentsVm = remember(registry, legacyAdapter) {
        UnifiedModelsViewModel(registry, legacyAdapter)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

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
        bottomBar = {
            BottomAppBar(
                containerColor = CustomColors.cardColorsOnSurfaceContainer.containerColor
            ) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(HugeIcons.AiBrain01, null) },
                    label = { Text(stringResource(R.string.setting_model_page_tab_model)) }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = { Icon(HugeIcons.AiEditing, null) },
                    label = { Text(stringResource(R.string.setting_model_page_tab_prompt)) }
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> ModelSettingsPage(
                    settings = settings,
                    vm = vm,
                    assignmentsVm = assignmentsVm,
                    contentPadding = contentPadding,
                )
                1 -> PromptSettingsPage(settings = settings, vm = vm, contentPadding = contentPadding)
            }
        }
    }
}

@Composable
private fun ModelSettingsPage(
    settings: me.rerere.rikkahub.data.datastore.Settings,
    vm: SettingVM,
    assignmentsVm: UnifiedModelsViewModel,
    contentPadding: PaddingValues,
) {
    val assignments by assignmentsVm.assignments.collectAsStateWithLifecycle()
    val legacyAssignments by assignmentsVm.legacyAssignments.collectAsStateWithLifecycle()
    val models by assignmentsVm.allModels.collectAsStateWithLifecycle()
    val repairState by assignmentsVm.repairState.collectAsStateWithLifecycle()

    LazyColumn(
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
                onFastModelSelected = { id -> vm.updateSettings(settings.copy(fastModelId = Uuid.parse(id))) },
                onSuggestionModelSelected = { id -> vm.updateSettings(settings.copy(suggestionModelId = id?.let(Uuid::parse))) },
                onCompressModelSelected = { id -> vm.updateSettings(settings.copy(compressModelId = Uuid.parse(id))) },
            )
        }
    }
}
