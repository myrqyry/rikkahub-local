package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.pages.assistant.AssistantPage
import org.koin.androidx.compose.koinViewModel

/**
 * Legacy saved route: older navigation stacks may still point at SettingAgent.
 * Render the facet list directly, without reintroducing an intermediate menu.
 */
@Composable
fun SettingAgentPage() {
    AssistantPage()
}

/** System-wide chat utilities: title, suggestion, translation, OCR and compression prompts. */
@Composable
fun SettingChatPromptsPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_default_prompts)) },
            navigationIcon = { BackButton() },
        )
    }) { padding ->
        PromptSettingsPage(settings = settings, vm = vm, contentPadding = padding)
    }
}
