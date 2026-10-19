package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.context.LocalTTSState

@Composable
fun TTSAutoPlay(vm: ChatVM, setting: Settings, conversation: Conversation) {
    val tts = LocalTTSState.current
    val updatedSetting by rememberUpdatedState(setting)
    var lastPlayedMessageId by remember(conversation.id) { mutableStateOf<kotlin.uuid.Uuid?>(null) }

    // The service broadcasts completed generation IDs for all conversations.
    // Read the current VM snapshot when the event arrives; the Composable
    // argument can still be one frame behind the service after a save.
    LaunchedEffect(vm, conversation.id) {
        vm.generationDoneFlow.collect { completedConversationId ->
            if (completedConversationId != conversation.id) return@collect
            val candidate = eligibleTtsAutoPlay(
                completedId = completedConversationId,
                current = vm.conversation.value,
                options = updatedSetting.displaySetting,
                lastPlayedMessageId = lastPlayedMessageId,
            ) ?: return@collect
            lastPlayedMessageId = candidate.messageId
            tts.speak(candidate.text)
        }
    }
}
