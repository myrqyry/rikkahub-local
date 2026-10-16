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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

/**
 * Represents different types of items in the conversation list
 */
sealed class ConversationListItem {
    data class DateHeader(
        val date: LocalDate,
        val label: String
    ) : ConversationListItem()
    data object PinnedHeader : ConversationListItem()
    data class Item(
        val conversation: Conversation
    ) : ConversationListItem()
}

@Composable
fun ColumnScope.ConversationList(
    current: Conversation,
    conversations: LazyPagingItems<ConversationListItem>,
    conversationJobs: Collection<Uuid>,
    listState: LazyListState,
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
        verticalArrangement = Arrangement.spacedBy(3.dp),
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
            key = conversations.itemKey { item ->
                when (item) {
                    is ConversationListItem.DateHeader -> "date_${item.date}"
                    is ConversationListItem.PinnedHeader -> "pinned_header"
                    is ConversationListItem.Item -> item.conversation.id.toString()
                }
            }
        ) { index ->
            when (val item = conversations[index]) {
                is ConversationListItem.DateHeader -> {
                    DateHeaderItem(
                        label = item.label,
                        modifier = Modifier.animateItem()
                    )
                }

                is ConversationListItem.PinnedHeader -> {
                    PinnedHeader(
                        modifier = Modifier.animateItem()
                    )
                }

                is ConversationListItem.Item -> {
                    ConversationItem(
                        conversation = item.conversation,
                        selected = item.conversation.id == current.id,
                        loading = item.conversation.id in conversationJobs,
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
private fun DateHeaderItem(
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PinnedHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = HugeIcons.Pin,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.pinned_chats),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    selected: Boolean,
    loading: Boolean,
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
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp)) {
        val content: @Composable BoxScope.() -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = { onClick(conversation) },
                        onLongClick = { showDropdownMenu = true },
                    )
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.title.ifBlank { stringResource(id = R.string.chat_page_new_message) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))

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
                    Text(stringResource(R.string.chat_page_move_to_folder))
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
