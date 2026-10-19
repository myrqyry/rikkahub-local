package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.icons.MingcuteIcons as HugeIcons
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.isNotConfigured
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.bubble.TranslateBubble
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

internal data class SettingsHomeItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val keywords: List<String> = emptyList(),
    val onClick: (() -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
)

internal data class SettingsHomeSection(
    val id: String,
    val title: String,
    val keywords: List<String> = emptyList(),
    val items: List<SettingsHomeItem>,
)

private val SettingsContentMaxWidth = 800.dp

@Composable
fun SettingPage(vm: SettingVM = koinViewModel()) {
    var activeSectionId by rememberSaveable { mutableStateOf<String?>(null) }
    val navController = LocalNavController.current
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val filesManager: FilesManager = koinInject()
    val storageState by produceState(-1 to 0L, filesManager) {
        value = filesManager.countChatFiles()
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(searchQuery.isNotBlank()) }

    if (settings.launchCount > 100 && (settings.launchCount - settings.sponsorAlertDismissedAt) >= 50) {
        AlertDialog(
            onDismissRequest = {
                vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
            },
            icon = { Icon(HugeIcons.WavingHand01, null) },
            title = { Text(stringResource(R.string.setting_page_sponsor_alert_title)) },
            text = { Text(stringResource(R.string.setting_page_sponsor_alert_desc)) },
            confirmButton = {
                Button(onClick = {
                    vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
                    navController.navigate(Screen.SettingDonate)
                }) {
                    Text(stringResource(R.string.setting_page_sponsor_alert_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
                }) {
                    Text(stringResource(R.string.setting_page_sponsor_alert_dismiss))
                }
            },
        )
    }

    val sections = listOf(
        SettingsHomeSection(
            id = "aiModels",
            title = stringResource(R.string.setting_home_group_ai_models),
            keywords = listOf("ai", "model", "provider", "assistant", "prompt"),
            items = listOf(
                SettingsHomeItem(
                    id = "models",
                    title = stringResource(R.string.setting_home_models),
                    description = stringResource(R.string.setting_home_models_desc),
                    icon = HugeIcons.Deepseek,
                    keywords = listOf("model", "provider", "default", "download", "local", "cloud", "litert", "endpoint", "api"),
                    onClick = { navController.navigate(Screen.Models()) },
                ),
                SettingsHomeItem(
                    id = "agentSettings",
                    title = stringResource(R.string.setting_page_agent),
                    description = stringResource(R.string.setting_page_agent_desc),
                    icon = HugeIcons.LookTop,
                    keywords = listOf("agent", "prompt", "system prompt", "reasoning", "thinking"),
                    onClick = { navController.navigate(Screen.SettingAgent) },
                ),
                SettingsHomeItem(
                    id = "assistants",
                    title = stringResource(R.string.setting_page_assistant),
                    description = stringResource(R.string.setting_page_assistant_desc),
                    icon = HugeIcons.LookTop,
                    keywords = listOf("agent", "persona", "prompt"),
                    onClick = { navController.navigate(Screen.Assistant) },
                ),
                SettingsHomeItem(
                    id = "promptLibrary",
                    title = stringResource(R.string.setting_home_prompt_library),
                    description = stringResource(R.string.setting_home_prompt_library_desc),
                    icon = HugeIcons.Book03,
                    keywords = listOf("prompt", "quick message", "template", "instruction"),
                    onClick = { navController.navigate(Screen.Prompts) },
                ),
                SettingsHomeItem(
                    id = "translateBubble",
                    title = stringResource(R.string.setting_page_translate_bubble),
                    description = stringResource(R.string.setting_page_translate_bubble_desc),
                    icon = HugeIcons.Translate,
                    keywords = listOf("translate", "bubble", "overlay", "agent"),
                    onClick = { TranslateBubble.show(context) },
                ),
            ),
        ),
        SettingsHomeSection(
            id = "experience",
            title = stringResource(R.string.setting_home_group_experience),
            keywords = listOf("chat", "voice", "message", "input", "speech", "appearance", "theme", "color"),
            items = listOf(
                SettingsHomeItem(
                    id = "chatBehavior",
                    title = stringResource(R.string.setting_page_chat_preferences),
                    description = stringResource(R.string.setting_page_preferences_general_desc),
                    icon = HugeIcons.Settings03,
                    keywords = listOf("behavior", "conversation", "generation", "general"),
                    onClick = { navController.navigate(Screen.SettingPreferencesGeneral) },
                ),
                SettingsHomeItem(
                    id = "chatInterface",
                    title = stringResource(R.string.setting_page_preferences_ui),
                    description = stringResource(R.string.setting_page_preferences_ui_desc),
                    icon = HugeIcons.Sun01,
                    keywords = listOf("interface", "message display", "layout", "ui"),
                    onClick = { navController.navigate(Screen.SettingPreferencesUI) },
                ),
                SettingsHomeItem(
                    id = "appearance",
                    title = stringResource(R.string.setting_page_appearance),
                    description = stringResource(R.string.setting_page_appearance_desc),
                    icon = HugeIcons.Image02,
                    keywords = listOf("theme", "color mode", "dark mode", "light mode", "dynamic color", "amoled", "palette"),
                    onClick = { navController.navigate(Screen.SettingTheme) },
                ),
                SettingsHomeItem(
                    id = "speech",
                    title = stringResource(R.string.setting_page_tts_service),
                    description = stringResource(R.string.setting_page_tts_service_desc),
                    icon = HugeIcons.Megaphone01,
                    keywords = listOf("tts", "asr", "whisper", "voice", "audio"),
                    onClick = { navController.navigate(Screen.SettingSpeech) },
                ),
                SettingsHomeItem(
                    id = "responseNotifications",
                    title = stringResource(R.string.setting_page_response_notifications),
                    description = stringResource(R.string.setting_page_response_notifications_desc),
                    icon = HugeIcons.MessageNotification01,
                    keywords = listOf("conversation", "response", "message alert"),
                    onClick = { navController.navigate(Screen.SettingPreferencesNotification) },
                ),
            ),
        ),
        SettingsHomeSection(
            id = "knowledgeTools",
            title = stringResource(R.string.setting_home_group_knowledge_tools),
            keywords = listOf("knowledge", "search", "rag", "retrieval", "browser", "tools", "skills", "plugins", "mcp", "workspace", "project", "folder"),
            items = listOf(
                SettingsHomeItem(
                    id = "search",
                    title = stringResource(R.string.setting_page_search_service),
                    description = stringResource(R.string.setting_page_search_service_desc),
                    icon = HugeIcons.GlobalSearch,
                    keywords = listOf("web search", "engine", "provider"),
                    onClick = { navController.navigate(Screen.SettingSearch) },
                ),
                SettingsHomeItem(
                    id = "rag",
                    title = stringResource(R.string.setting_home_rag),
                    description = stringResource(R.string.setting_home_rag_desc),
                    icon = HugeIcons.Database02,
                    keywords = listOf("retrieval", "embedding", "vector", "documents"),
                    onClick = { navController.navigate(Screen.SettingRAG) },
                ),
                SettingsHomeItem(
                    id = "browser",
                    title = stringResource(R.string.setting_page_browser),
                    description = stringResource(R.string.setting_page_browser_desc),
                    icon = HugeIcons.Earth,
                    keywords = listOf("webview", "headless", "website", "internet"),
                    onClick = { navController.navigate(Screen.SettingBrowser) },
                ),
                SettingsHomeItem(
                    id = "skills",
                    title = stringResource(R.string.setting_home_skills_page),
                    description = stringResource(R.string.setting_home_skills_page_desc),
                    icon = HugeIcons.Book03,
                    keywords = listOf("agent", "expertise", "workflow", "skills.sh"),
                    onClick = { navController.navigate(Screen.Skills) },
                ),
                SettingsHomeItem(
                    id = "mcp",
                    title = stringResource(R.string.setting_page_mcp),
                    description = stringResource(R.string.setting_page_mcp_desc),
                    icon = HugeIcons.McpServer,
                    keywords = listOf("server", "protocol", "connector"),
                    onClick = { navController.navigate(Screen.SettingMcp) },
                ),
                SettingsHomeItem(
                    id = "plugins",
                    title = stringResource(R.string.setting_home_plugins),
                    description = stringResource(R.string.setting_home_plugins_desc),
                    icon = HugeIcons.Package,
                    keywords = listOf("installed", "commands", "tools"),
                    onClick = { navController.navigate(Screen.SettingPlugin) },
                ),
                SettingsHomeItem(
                    id = "workspaces",
                    title = stringResource(R.string.setting_home_workspaces),
                    description = stringResource(R.string.setting_home_workspaces_desc),
                    icon = HugeIcons.Folder01,
                    keywords = listOf("workspace", "project", "folder", "drive", "portable"),
                    onClick = { navController.navigate(Screen.Workspaces) },
                ),
            ),
        ),
        SettingsHomeSection(
            id = "automation",
            title = stringResource(R.string.setting_home_group_automation_device),
            keywords = listOf("automation", "workflow", "schedule", "server", "telegram", "notification", "accessibility", "shell", "terminal"),
            items = listOf(
                SettingsHomeItem(
                    id = "webServer",
                    title = stringResource(R.string.setting_page_web_server),
                    description = stringResource(R.string.setting_page_web_server_desc),
                    icon = HugeIcons.ServerStack01,
                    keywords = listOf("remote", "api", "http", "web"),
                    onClick = { navController.navigate(Screen.SettingWeb) },
                ),
                SettingsHomeItem(
                    id = "workflows",
                    title = stringResource(R.string.setting_page_workflows),
                    description = stringResource(R.string.setting_page_workflows_desc),
                    icon = HugeIcons.Connect,
                    keywords = listOf("flow", "pipeline", "agent"),
                    onClick = { navController.navigate(Screen.SettingWorkflows) },
                ),
                SettingsHomeItem(
                    id = "scheduledJobs",
                    title = stringResource(R.string.setting_page_scheduled_jobs),
                    description = stringResource(R.string.setting_page_scheduled_jobs_desc),
                    icon = HugeIcons.Clock02,
                    keywords = listOf("cron", "timer", "background", "jobs"),
                    onClick = { navController.navigate(Screen.SettingScheduledJobs) },
                ),
                SettingsHomeItem(
                    id = "telegram",
                    title = stringResource(R.string.setting_page_telegram),
                    description = stringResource(R.string.setting_page_telegram_desc),
                    icon = HugeIcons.Telegram,
                    keywords = listOf("bot", "messaging", "remote"),
                    onClick = { navController.navigate(Screen.SettingTelegram) },
                ),
                SettingsHomeItem(
                    id = "notificationAccess",
                    title = stringResource(R.string.setting_page_notification_access),
                    description = stringResource(R.string.setting_page_notification_access_desc),
                    icon = HugeIcons.Alert01,
                    keywords = listOf("android notification", "channel", "system"),
                    onClick = { navController.navigate(Screen.SettingNotifications) },
                ),
                SettingsHomeItem(
                    id = "accessibility",
                    title = stringResource(R.string.setting_page_accessibility),
                    description = stringResource(R.string.setting_page_accessibility_desc),
                    icon = HugeIcons.SmartPhone01,
                    keywords = listOf("android", "service", "device control", "automation"),
                    onClick = { navController.navigate(Screen.SettingAccessibility) },
                ),
                SettingsHomeItem(
                    id = "termux",
                    title = stringResource(R.string.setting_page_termux),
                    description = stringResource(R.string.setting_page_termux_desc),
                    icon = HugeIcons.Console,
                    keywords = listOf("shell", "terminal", "command", "android"),
                    onClick = { navController.navigate(Screen.SettingTermux) },
                ),
            ),
        ),
        SettingsHomeSection(
            id = "privacySafety",
            title = stringResource(R.string.setting_home_group_privacy_safety),
            keywords = listOf("safety", "privacy", "permission", "approval", "trust", "security"),
            items = listOf(
                SettingsHomeItem(
                    id = "permissions",
                    title = stringResource(R.string.setting_page_permissions),
                    description = stringResource(R.string.setting_page_permissions_desc),
                    icon = HugeIcons.Shield01,
                    keywords = listOf("privacy", "android permission", "access"),
                    onClick = { navController.navigate(Screen.SettingPermissions) },
                ),
                SettingsHomeItem(
                    id = "toolApprovals",
                    title = stringResource(R.string.setting_page_tool_approvals),
                    description = stringResource(R.string.setting_page_tool_approvals_desc),
                    icon = HugeIcons.Tick01,
                    keywords = listOf("approval", "trust", "tool execution", "security"),
                    onClick = { navController.navigate(Screen.SettingToolApprovals) },
                ),
            ),
        ),
        SettingsHomeSection(
            id = "dataMaintenance",
            title = stringResource(R.string.setting_home_group_data_maintenance),
            keywords = listOf("data", "storage", "backup", "files", "export", "restore", "log", "doctor", "developer", "about"),
            items = buildList {
                add(
                    SettingsHomeItem(
                        id = "backup",
                        title = stringResource(R.string.setting_page_data_backup),
                        description = stringResource(R.string.setting_page_data_backup_desc),
                        icon = HugeIcons.Download01,
                        keywords = listOf("restore", "export", "import", "sync"),
                        onClick = { navController.navigate(Screen.Backup) },
                    )
                )
                add(
                    SettingsHomeItem(
                        id = "chatStorage",
                        title = stringResource(R.string.setting_page_chat_storage),
                        description = if (storageState.first == -1) {
                            stringResource(R.string.calculating)
                        } else {
                            stringResource(
                                R.string.setting_page_chat_storage_desc,
                                storageState.first,
                                storageState.second / 1024 / 1024.0,
                            )
                        },
                        icon = HugeIcons.Folder01,
                        keywords = listOf("attachments", "cache", "cleanup", "disk"),
                        onClick = { navController.navigate(Screen.SettingFiles) },
                    )
                )
                add(
                    SettingsHomeItem(
                        id = "requestLogs",
                        title = stringResource(R.string.setting_page_request_logs),
                        description = stringResource(R.string.setting_page_request_logs_desc),
                        icon = HugeIcons.Bookshelf01,
                        keywords = listOf("network", "api", "debug", "history"),
                        onClick = { navController.navigate(Screen.Log) },
                    )
                )
                add(
                    SettingsHomeItem(
                        id = "doctor",
                        title = stringResource(R.string.setting_page_doctor),
                        description = stringResource(R.string.setting_page_doctor_desc),
                        icon = HugeIcons.Wrench01,
                        keywords = listOf("health check", "repair", "troubleshoot", "diagnostics"),
                        onClick = { navController.navigate(Screen.SettingDoctor) },
                    )
                )
                if (settings.developerMode) {
                    add(
                        SettingsHomeItem(
                            id = "developer",
                            title = stringResource(R.string.setting_home_developer_tools),
                            description = stringResource(R.string.setting_home_developer_tools_desc),
                            icon = HugeIcons.Code,
                            keywords = listOf("advanced", "debug", "developer"),
                            onClick = { navController.navigate(Screen.Developer) },
                        )
                    )
                }
                add(
                    SettingsHomeItem(
                        id = "about",
                        title = stringResource(R.string.setting_page_about),
                        description = stringResource(R.string.setting_page_about_desc),
                        icon = HugeIcons.WavingHand01,
                        keywords = listOf("version", "info", "credits", "help"),
                        onClick = { navController.navigate(Screen.SettingAbout) },
                    )
                )
            },
        ),
    )
    val filteredSections = filterSettingsSections(sections, searchQuery)

    // One screen of visible categories. A tap opens its destinations; searching
    // always searches all categories, including while inside a section.
    val activeSection = sections.firstOrNull { it.id == activeSectionId }
    val searching = searchQuery.isNotBlank()
    BackHandler(enabled = searchExpanded || activeSectionId != null) {
        if (searchExpanded) {
            searchQuery = ""
            searchExpanded = false
        } else {
            activeSectionId = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (activeSection != null && !searchExpanded) activeSection.title else stringResource(R.string.settings)) },
                navigationIcon = {
                    when {
                        searchExpanded -> IconButton(onClick = { searchQuery = ""; searchExpanded = false }) {
                            Icon(HugeIcons.ArrowLeft01, contentDescription = stringResource(R.string.settings))
                        }
                        activeSection != null -> IconButton(onClick = { activeSectionId = null }) {
                            Icon(HugeIcons.ArrowLeft01, contentDescription = stringResource(R.string.settings))
                        }
                        else -> BackButton()
                    }
                },
                colors = CustomColors.topBarColors,
                actions = {
                    IconButton(onClick = {
                        if (searchExpanded) {
                            searchQuery = ""
                            searchExpanded = false
                        } else {
                            searchExpanded = true
                        }
                    }) {
                        Icon(
                            if (searchExpanded) HugeIcons.Cancel01 else HugeIcons.Search01,
                            contentDescription = stringResource(R.string.setting_home_search_placeholder),
                        )
                    }
                },
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = SettingsContentMaxWidth).fillMaxSize(),
                contentPadding = innerPadding + PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (searchExpanded) {
                    item("settingsSearch") {
                        SettingsSearchField(query = searchQuery, onQueryChange = { searchQuery = it })
                    }
                }
                if (settings.isNotConfigured() && !searching && activeSection == null) {
                    item("providerWarning") { ProviderConfigWarningCard(navController) }
                }
                when {
                    searching -> {
                        if (filteredSections.isEmpty()) {
                            item("noResults") { SettingsNoResultsCard() }
                        } else {
                            filteredSections.forEach { section ->
                                item("search-heading-${section.id}") {
                                    SettingsSectionHeading(section.title)
                                }
                                items(section.items, key = { "search-${section.id}-${it.id}" }) { item ->
                                    SettingsDestinationRow(item)
                                }
                            }
                        }
                    }
                    activeSection != null -> {
                        items(activeSection.items, key = { "destination-${it.id}" }) { item ->
                            SettingsDestinationRow(item)
                        }
                    }
                    else -> {
                        items(sections, key = { "category-${it.id}" }) { section ->
                            SettingsSectionRow(section, onClick = { activeSectionId = section.id })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionRow(
    section: SettingsHomeSection,
    onClick: () -> Unit,
) {
    val overview = section.items.take(2).joinToString(" · ") { it.title }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                section.items.first().icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                HugeIcons.ArrowRight01,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
}

@Composable
private fun SettingsSectionHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsDestinationRow(item: SettingsHomeItem) {
    Surface(
        onClick = { item.onClick?.invoke() },
        enabled = item.onClick != null,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.description.isNotBlank()) {
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.trailingContent != null) item.trailingContent.invoke()
            else Icon(
                HugeIcons.ArrowRight01,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
}

@Composable
private fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.setting_home_search_placeholder)) },
        leadingIcon = {
            Icon(
                HugeIcons.GlobalSearch,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(onClick = { onQueryChange("") }) {
                    Text(stringResource(R.string.setting_home_clear_search))
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsNoResultsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.setting_home_no_results)) },
            supportingContent = { Text(stringResource(R.string.setting_home_no_results_desc)) },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
    }
}

/**
 * Match at word starts, not arbitrary substrings: "rag" should never hit "storage".
 * Accept whole multi-word prefixes so "notification acc" finds "Notification access".
 */
internal fun String.matchesSettingsQuery(query: String): Boolean {
    val separators = Regex("[^\\p{L}\\p{N}]+")
    val words = lowercase().split(separators).filter { it.isNotEmpty() }
    val terms = query.trim().lowercase().split(separators).filter { it.isNotEmpty() }
    if (terms.isEmpty() || terms.size > words.size) return false
    return words.windowed(terms.size).any { window ->
        window.indices.all { i -> window[i].startsWith(terms[i]) }
    }
}

internal fun filterSettingsSections(
    sections: List<SettingsHomeSection>,
    query: String,
): List<SettingsHomeSection> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return sections

    return sections.mapNotNull { section ->
        // Only the visible category title expands an entire section.
        // Matching a section's broad search keyword should not dump every item.
        val sectionMatches = section.title.matchesSettingsQuery(normalizedQuery)
        val matchingItems = if (sectionMatches) section.items
        else section.items.filter { it.matches(normalizedQuery) }
        if (matchingItems.isEmpty()) null
        else section.copy(items = matchingItems)
    }
}

internal fun SettingsHomeItem.matches(query: String): Boolean {
    return title.matchesSettingsQuery(query) ||
        description.matchesSettingsQuery(query) ||
        keywords.any { it.matchesSettingsQuery(query) }
}

@Composable
private fun ProviderConfigWarningCard(navController: Navigator) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_page_config_api_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.setting_page_config_api_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(
                onClick = { navController.navigate(Screen.Models(scrollToSources = true)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.setting_page_config))
            }
        }
    }
}
