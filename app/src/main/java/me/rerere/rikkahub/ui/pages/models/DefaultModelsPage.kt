package me.rerere.rikkahub.ui.pages.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.pages.models.components.ModelAssignmentsSection
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun DefaultModelsPage(
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val assignmentsVm: UnifiedModelsViewModel = koinViewModel()
    LaunchedEffect(Unit) {
        assignmentsVm.setTab(ModelTab.ALL)
        assignmentsVm.setSearch("")
        assignmentsVm.setSourceFilter(ModelSourceFilter.ALL)
        assignmentsVm.setProviderFilter(null)
    }

    val assignments by assignmentsVm.assignments.collectAsStateWithLifecycle()
    val legacyAssignments by assignmentsVm.legacyAssignments.collectAsStateWithLifecycle()
    val models by assignmentsVm.allModels.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_default_models_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
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
                    repairState = null,
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
        }
    }
}
