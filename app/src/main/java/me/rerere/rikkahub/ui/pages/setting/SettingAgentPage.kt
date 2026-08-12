package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingAgentPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Agent Settings") }, navigationIcon = { BackButton() })
    }) { padding ->
        PromptSettingsPage(settings = settings, vm = vm, contentPadding = padding)
    }
}
