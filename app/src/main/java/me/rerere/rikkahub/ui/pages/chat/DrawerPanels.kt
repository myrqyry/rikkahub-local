package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.neumorphic.NeumorphicElevation
import dev.neumorphic.NeumorphicRaisedSurface
import dev.neumorphic.NeumorphicShape
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.ui.components.ui.Greeting
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.icons.MingcuteIcons as HugeIcons
import me.rerere.rikkahub.ui.theme.rememberRikkaNeumorphicColors
import me.rerere.rikkahub.utils.navigateToChatPage
import kotlin.uuid.Uuid

@Composable
internal fun DrawerAssistantHeader(
    assistant: Assistant,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
) {
    val name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }
    NeumorphicRaisedSurface(
        modifier = Modifier.fillMaxWidth(),
        colors = rememberRikkaNeumorphicColors(),
        shape = NeumorphicShape.Rounded(20.dp),
        elevation = NeumorphicElevation.Subtle,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UIAvatar(
                name = name,
                value = assistant.avatar,
                onClick = onEdit,
                modifier = Modifier.size(44.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Greeting(style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onSwitch, modifier = Modifier.size(44.dp)) {
                Icon(HugeIcons.TransferHorizontal, contentDescription = stringResource(R.string.chat_drawer_switch_assistant))
            }
        }
    }
}

@Composable
internal fun DrawerProjectPicker(
    folders: List<Folder>,
    selectedFolderId: Uuid?,
    onSelect: (Uuid?) -> Unit,
    onCreate: () -> Unit,
    onRename: (Folder) -> Unit,
    onDelete: (Folder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = folders.firstOrNull { it.id == selectedFolderId }
    Box(modifier) {
        NeumorphicRaisedSurface(
            modifier = Modifier.fillMaxWidth(),
            colors = rememberRikkaNeumorphicColors(),
            shape = NeumorphicShape.Rounded(16.dp),
            elevation = NeumorphicElevation.Subtle,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(HugeIcons.Folder01, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = selected?.name ?: stringResource(R.string.chat_drawer_all_chats),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(HugeIcons.ArrowDown01, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_drawer_all_chats)) },
                leadingIcon = { Icon(HugeIcons.AiChat01, null) },
                onClick = { expanded = false; onSelect(null) },
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(HugeIcons.Folder01, null) },
                    onClick = { expanded = false; onSelect(folder.id) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_drawer_new_project)) },
                leadingIcon = { Icon(HugeIcons.FolderAdd, null) },
                onClick = { expanded = false; onCreate() },
            )
            if (selected != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_drawer_rename_project)) },
                    leadingIcon = { Icon(HugeIcons.PencilEdit01, null) },
                    onClick = { expanded = false; onRename(selected) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_drawer_delete_project)) },
                    leadingIcon = { Icon(HugeIcons.Delete01, null) },
                    onClick = { expanded = false; onDelete(selected) },
                )
            }
        }
    }
}

@Composable
internal fun DrawerQuickActions(navController: Navigator) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { navigateToChatPage(navController) }, modifier = Modifier.size(44.dp)) {
        Icon(HugeIcons.PencilEdit01, contentDescription = stringResource(R.string.chat_page_new_chat))
    }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(44.dp)) {
            Icon(HugeIcons.MoreVertical, contentDescription = stringResource(R.string.chat_drawer_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            @Composable
            fun link(icon: ImageVector, label: String, destination: Screen) {
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = { expanded = false; navController.navigate(destination) },
                )
            }
            link(HugeIcons.Search01, stringResource(R.string.chat_page_search_chats), Screen.MessageSearch)
            link(HugeIcons.TransactionHistory, stringResource(R.string.chat_page_history), Screen.History)
            link(HugeIcons.Cpu, stringResource(R.string.models_title), Screen.Models())
            link(HugeIcons.Image02, stringResource(R.string.chat_drawer_images), Screen.ImageGen)
            link(HugeIcons.LookTop, stringResource(R.string.assistant_page_title), Screen.Assistant)
            link(HugeIcons.Wrench01, stringResource(R.string.extensions_page_title), Screen.Extensions)
            link(HugeIcons.InLove, stringResource(R.string.favorite_page_title), Screen.Favorite)
            link(HugeIcons.ChartColumn, stringResource(R.string.chat_drawer_statistics), Screen.Stats)
        }
    }
}
