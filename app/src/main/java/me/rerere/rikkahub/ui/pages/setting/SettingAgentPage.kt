package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.icons.MingcuteIcons as HugeIcons
import org.koin.androidx.compose.koinViewModel

/**
 * Agent configuration and per-assistant overrides are already implemented on
 * AssistantDetailPage. This page deliberately links to those canonical editors
 * instead of duplicating controls or inventing global inheritance semantics.
 */
@Composable
fun SettingAgentPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val defaultAssistant = settings.assistants.firstOrNull { it.id == DEFAULT_ASSISTANT_ID }
        ?: settings.assistants.firstOrNull()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_agents_assistants)) },
            navigationIcon = { BackButton() },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            AgentSettingsDestinationRow(
                title = stringResource(R.string.settings_default_assistant),
                subtitle = stringResource(R.string.settings_default_assistant_desc),
                onClick = if (defaultAssistant == null) null else {
                    { navController.navigate(Screen.AssistantDetail(defaultAssistant.id.toString())) }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            AgentSettingsDestinationRow(
                title = stringResource(R.string.settings_individual_assistants),
                subtitle = stringResource(R.string.settings_individual_assistants_desc),
                onClick = { navController.navigate(Screen.Assistant) },
            )
        }
    }
}

@Composable
private fun AgentSettingsDestinationRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    Surface(onClick = { onClick?.invoke() }, enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(), color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(HugeIcons.ArrowRight01, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
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
