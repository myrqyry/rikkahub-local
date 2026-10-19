package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.ui.icons.MingcuteIcons as HugeIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.neumorphic.NeumorphicElevation
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.NeumorphicInsetSurface
import me.rerere.rikkahub.ui.theme.rememberRikkaNeumorphicColors
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.theme.extendColors
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

/**
 * Represents different types of items in the conversation list
 */
sealed class ConversationListItem {
    data class Item(val conversation: Conversation) : ConversationListItem()
}

@Composable
fun ColumnScope.ConversationList(
    current: Conversation,
    conversations: LazyPagingItems<ConversationListItem>,
    conversationJobs: Collection<Uuid>,
    listState: LazyListState,
    projectNames: Map<Uuid, String>,
    showProjectBadges: Boolean,
    modifier: Modifier = Modifier,
    onClick: (Conversation) -> Unit = {},
    onDelete: (Conversation) -> Unit = {},
    onRegenerateTitle: (Conversation) -> Unit = {},
    onPin: (Conversation) -> Unit = {},
    onMoveToAssistant: (Conversation) -> Unit = {},
    onMoveToFolder: (Conversation) -> Unit = {}
) {
    var hasScrolledToCurrent by remember(current.id) { mutableStateOf(false) }

    LaunchedEffect(current.id, conversations.itemCount, hasScrolledToCurrent) {
        if (hasScrolledToCurrent) return@LaunchedEffect
        val currentIndex = conversations.itemSnapshotList.items.indexOfFirst {
            (it as? ConversationListItem.Item)?.conversation?.id == current.id
        }
        if (currentIndex >= 0) {
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == currentIndex }
            if (!isVisible) {
                listState.scrollToItem(currentIndex)
            }
            hasScrolledToCurrent = true
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (conversations.itemCount == 0) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = stringResource(id = R.string.chat_page_no_conversations),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(
            count = conversations.itemCount,
            key = conversations.itemKey { (it as ConversationListItem.Item).conversation.id.toString() }
        ) { index ->
            when (val item = conversations[index]) {
                is ConversationListItem.Item -> {
                    ConversationItem(
                        conversation = item.conversation,
                        selected = item.conversation.id == current.id,
                        loading = item.conversation.id in conversationJobs,
                        projectName = if (showProjectBadges) item.conversation.folderId?.let(projectNames::get) else null,
                        onClick = onClick,
                        onDelete = onDelete,
                        onRegenerateTitle = onRegenerateTitle,
                        onPin = onPin,
                        onMoveToAssistant = onMoveToAssistant,
                        onMoveToFolder = onMoveToFolder,
                        modifier = Modifier.animateItem()
                    )
                }

                null -> {
                    // Placeholder for loading state
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    selected: Boolean,
    loading: Boolean,
    projectName: String?,
    modifier: Modifier = Modifier,
    onDelete: (Conversation) -> Unit = {},
    onRegenerateTitle: (Conversation) -> Unit = {},
    onPin: (Conversation) -> Unit = {},
    onMoveToAssistant: (Conversation) -> Unit = {},
    onMoveToFolder: (Conversation) -> Unit = {},
    onClick: (Conversation) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = rememberRikkaNeumorphicColors()
    var showDropdownMenu by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val updated = conversation.updateAt.atZone(zone)
    val timestamp = when (updated.toLocalDate()) {
        LocalDate.now(zone) -> updated.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        LocalDate.now(zone).minusDays(1) -> stringResource(R.string.chat_page_yesterday)
        else -> updated.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
    }
    val title = conversation.title.ifBlank {
        conversation.createAt.atZone(zone)
            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"))
    }
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        val content: @Composable BoxScope.() -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = { onClick(conversation) },
                        onLongClick = { showDropdownMenu = true },
                    )
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (projectName != null) {
                    Icon(
                        imageVector = HugeIcons.Folder01,
                        contentDescription = projectName,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp).size(16.dp),
                    )
                }
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.title.isNotBlank()) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                // 置顶图标
                AnimatedVisibility(conversation.isPinned) {
                    Icon(
                        imageVector = HugeIcons.Pin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                AnimatedVisibility(loading) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.extendColors.green6)
                            .size(4.dp)
                            .semantics {
                                contentDescription = "Loading"
                            }
                    )
                }
            }
        }
        if (selected) {
            NeumorphicInsetSurface(
                modifier = Modifier.fillMaxWidth(),
                colors = colors,
                shape = NeumorphicShape.Rounded(18.dp),
                elevation = NeumorphicElevation.Subtle,
                backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                content = content,
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth(), content = content)
        }
        DropdownMenu(
            expanded = showDropdownMenu,
            onDismissRequest = { showDropdownMenu = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (conversation.isPinned) stringResource(R.string.unpin_chat) else stringResource(R.string.pin_chat)
                    )
                },
                onClick = {
                    onPin(conversation)
                    showDropdownMenu = false
                },
                leadingIcon = {
                    Icon(
                        if (conversation.isPinned) HugeIcons.PinOff else HugeIcons.Pin,
                        null
                    )
                }
            )

            DropdownMenuItem(
                text = {
                    Text(stringResource(id = R.string.chat_page_regenerate_title))
                },
                onClick = {
                    onRegenerateTitle(conversation)
                    showDropdownMenu = false
                },
                leadingIcon = {
                    Icon(HugeIcons.Refresh01, null)
                }
            )

            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.chat_page_move_to_assistant))
                },
                onClick = {
                    onMoveToAssistant(conversation)
                    showDropdownMenu = false
                },
                leadingIcon = {
                    Icon(HugeIcons.Forward02, null)
                }
            )

            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.chat_drawer_move_to_project))
                },
                onClick = {
                    onMoveToFolder(conversation)
                    showDropdownMenu = false
                },
                leadingIcon = {
                    Icon(HugeIcons.Folder01, null)
                }
            )

            DropdownMenuItem(
                text = {
                    Text(stringResource(id = R.string.chat_page_delete))
                },
                onClick = {
                    onDelete(conversation)
                    showDropdownMenu = false
                },
                leadingIcon = {
                    Icon(HugeIcons.Delete01, null)
                }
            )
        }
        }
    }
