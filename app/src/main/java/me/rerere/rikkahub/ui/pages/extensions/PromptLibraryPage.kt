package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

enum class PromptLibraryTab {
    INSTRUCTIONS,
    QUICK_MESSAGES,
}

@Composable
fun PromptLibraryPage(
    initialTab: PromptLibraryTab = PromptLibraryTab.INSTRUCTIONS,
    promptVM: PromptVM = koinViewModel(),
    quickMessagesVM: QuickMessagesVM = koinViewModel(),
) {
    val promptSettings by promptVM.settings.collectAsStateWithLifecycle()
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_home_prompt_library)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == PromptLibraryTab.INSTRUCTIONS,
                    onClick = { selectedTab = PromptLibraryTab.INSTRUCTIONS },
                    text = { Text(stringResource(R.string.prompt_page_title)) },
                )
                Tab(
                    selected = selectedTab == PromptLibraryTab.QUICK_MESSAGES,
                    onClick = { selectedTab = PromptLibraryTab.QUICK_MESSAGES },
                    text = { Text(stringResource(R.string.assistant_page_quick_messages)) },
                )
            }
            when (selectedTab) {
                PromptLibraryTab.INSTRUCTIONS -> PromptPageContent(
                    settings = promptSettings,
                    onUpdateSettings = promptVM::updateSettings,
                    onDeleteModeInjection = promptVM::deleteModeInjection,
                    onDeleteLorebook = promptVM::deleteLorebook,
                )

                PromptLibraryTab.QUICK_MESSAGES -> QuickMessagesContent(
                    vm = quickMessagesVM,
                )
            }
        }
    }
}
